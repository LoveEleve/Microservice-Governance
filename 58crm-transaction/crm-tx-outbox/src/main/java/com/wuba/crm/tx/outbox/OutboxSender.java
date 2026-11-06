package com.wuba.crm.tx.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 独立发送线程的核心逻辑。
 *
 * <p>对应 Kafka 的 {@code Sender}：从有界 buffer 取消息，等待 linger 窗口，
 * 按 partition（业务线）分组，执行拦截器、序列化，批量写入 Redis Stream，
 * 最后推进 MySQL 状态。</p>
 */
public class OutboxSender {

    private static final Logger log = LoggerFactory.getLogger(OutboxSender.class);

    private final OutboxBuffer buffer;
    private final RedisStreamPublisher publisher;
    private final OutboxInterceptorChain interceptors;
    private final OutboxPartitioner partitioner;
    private final OutboxSerializer serializer;
    private final LocalMessageRepository repository;
    private final OutboxConfig config;
    private final OutboxInFlight inFlight;

    public OutboxSender(
            OutboxBuffer buffer,
            RedisStreamPublisher publisher,
            OutboxInterceptorChain interceptors,
            OutboxPartitioner partitioner,
            OutboxSerializer serializer,
            LocalMessageRepository repository,
            OutboxConfig config,
            OutboxInFlight inFlight) {
        this.buffer = buffer;
        this.publisher = publisher;
        this.interceptors = interceptors;
        this.partitioner = partitioner;
        this.serializer = serializer;
        this.repository = repository;
        this.config = config;
        this.inFlight = inFlight;
    }

    /**
     * 发送一批消息。没有消息时阻塞 linger 后返回 0。
     *
     * @return 成功写入 Stream 的消息数
     */
    public int sendOnce() throws InterruptedException {
        OutboxRecord first = buffer.poll(config.linger());
        if (first == null) {
            return 0;
        }

        List<OutboxRecord> batch = new ArrayList<>(config.batchSize());
        batch.add(first);
        long deadline = System.nanoTime() + config.linger().toNanos();
        while (batch.size() < config.batchSize()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                break;
            }
            OutboxRecord next = buffer.poll(
                    first.bizCode(), java.time.Duration.ofNanos(remaining));
            if (next == null) {
                break;
            }
            batch.add(next);
        }

        try {
            List<OutboxRecord> intercepted = new ArrayList<>(batch.size());
            for (OutboxRecord record : batch) {
                OutboxRecord current = interceptors.onSend(record);
                intercepted.add(current == null ? record : current);
            }

            Map<String, List<OutboxRecord>> byStream = new LinkedHashMap<>();
            for (OutboxRecord record : intercepted) {
                String stream = config.streamPrefix() + partitioner.route(record);
                byStream.computeIfAbsent(stream, ignored -> new ArrayList<>()).add(record);
            }

            int sent = 0;
            Instant nextRetryAt = Instant.now().plus(config.retryAfter());
            for (Map.Entry<String, List<OutboxRecord>> entry : byStream.entrySet()) {
                String stream = entry.getKey();
                List<OutboxRecord> records = entry.getValue();
                List<Map<String, String>> fields = new ArrayList<>(records.size());
                for (OutboxRecord record : records) {
                    fields.add(serializer.serialize(record));
                }

                try {
                    publisher.publish(stream, fields);
                } catch (RuntimeException ex) {
                    publishFailed(records, nextRetryAt, ex);
                    continue;
                }

                try {
                    repository.markSentBatch(toLocalMessages(records), nextRetryAt);
                    for (OutboxRecord record : records) {
                        interceptors.onAcknowledgement(
                                new OutboxSendResult(stream, record.msgId(), null), null);
                    }
                    sent += records.size();
                } catch (RuntimeException ex) {
                    log.warn("mark sent failed after XADD, stream={}, size={}",
                            stream, records.size(), ex);
                    for (OutboxRecord record : records) {
                        interceptors.onAcknowledgement(
                                new OutboxSendResult(stream, record.msgId(), null), ex);
                    }
                }
            }
            return sent;
        } catch (RuntimeException ex) {
            publishFailed(batch, Instant.now().plus(config.retryAfter()), ex);
            return 0;
        } finally {
            for (OutboxRecord record : batch) {
                inFlight.remove(record.msgId());
            }
        }
    }

    private void publishFailed(List<OutboxRecord> records, Instant nextRetryAt, RuntimeException error) {
        log.warn("stream publish failed, size={}", records.size(), error);
        for (OutboxRecord record : records) {
            interceptors.onAcknowledgement(
                    new OutboxSendResult(config.streamPrefix() + record.bizCode(), record.msgId(), null),
                    error);
        }
        try {
            repository.markFailedBatch(
                    toLocalMessages(records), nextRetryAt, config.maxRetries());
        } catch (RuntimeException markError) {
            log.warn("mark failed batch failed, size={}", records.size(), markError);
        }
    }

    private List<LocalMessage> toLocalMessages(List<OutboxRecord> records) {
        List<LocalMessage> messages = new ArrayList<>(records.size());
        for (OutboxRecord record : records) {
            messages.add(record.toLocalMessage());
        }
        return messages;
    }
}
