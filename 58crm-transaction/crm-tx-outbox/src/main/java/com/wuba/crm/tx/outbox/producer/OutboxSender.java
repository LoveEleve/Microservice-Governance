package com.wuba.crm.tx.outbox.producer;

import com.wuba.crm.tx.outbox.domain.LocalMessage;
import com.wuba.crm.tx.outbox.domain.OutboxRecord;
import com.wuba.crm.tx.outbox.domain.OutboxSendResult;
import com.wuba.crm.tx.outbox.persistence.LocalMessageRepository;
import com.wuba.crm.tx.outbox.pipeline.interceptor.OutboxInterceptorChain;
import com.wuba.crm.tx.outbox.pipeline.route.OutboxPartitioner;
import com.wuba.crm.tx.outbox.pipeline.serializer.OutboxSerializer;
import com.wuba.crm.tx.outbox.pipeline.stream.RedisStreamPublisher;

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

    // 发送线程只从这里取消息；它不直接访问 MySQL 扫描逻辑。
    private final OutboxBuffer buffer;
    // Stream 写入端口，测试时可替换成 fake。
    private final RedisStreamPublisher publisher;
    // 发送前/发送后的横切逻辑，例如 trace、审计、指标。
    private final OutboxInterceptorChain interceptors;
    // bizCode -> Stream 的路由策略。
    private final OutboxPartitioner partitioner;
    // record -> Stream field/value。
    private final OutboxSerializer serializer;
    // 发送成功后回写 MySQL 状态。
    private final LocalMessageRepository repository;
    private final OutboxConfig config;
    // 与 Scanner 共享，发送结束必须释放，否则消息永远不再被扫描。
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
        // 1. 阻塞等待第一条消息。没有消息时由 poll 超时返回 null。
        OutboxRecord first = buffer.poll(config.linger());
        if (first == null) {
            return 0;
        }

        // 2. 第一条决定这一批的 bizCode；在 linger 窗口内继续聚合同一业务线。
        //    这就是 Kafka linger.ms 的简化实现：不是有第一条就立刻发。
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

        // 拦截器只允许修改事件内容（payload/header），不允许改身份字段。
        // 状态回写必须使用原始 batch，否则拦截器改 msgId/bizId/status 会写错行。
        Map<String, OutboxRecord> originalByMsgId = new LinkedHashMap<>();
        for (OutboxRecord record : batch) {
            originalByMsgId.put(record.msgId(), record);
        }

        try {
            // 3. 执行拦截器。拦截器链内部已经做了异常隔离。
            List<OutboxRecord> intercepted = new ArrayList<>(batch.size());
            for (OutboxRecord record : batch) {
                OutboxRecord current = interceptors.onSend(record);
                intercepted.add(current == null ? record : current);
            }

            // 4. 按路由结果分组。正常是一批一个 bizCode；自定义 partitioner 可能拆成多个 Stream。
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
                // 5. 身份字段必须用原始 record；拦截后的 record 只用于序列化。
                List<OutboxRecord> originals = originalsOf(records, originalByMsgId);
                List<Map<String, String>> fields = new ArrayList<>(records.size());
                for (OutboxRecord record : records) {
                    fields.add(serializer.serialize(record));
                }

                // 6. XADD pipeline。失败时整批标记失败，等下一轮退避重试。
                try {
                    publisher.publish(stream, fields);
                } catch (RuntimeException ex) {
                    publishFailed(originals, nextRetryAt, ex);
                    continue;
                }

                // 7. XADD 成功后才允许把 INIT 推进到 SENT。
                //    如果这里失败，消息仍是 INIT，下一轮会重复 XADD，由消费幂等兜底。
                try {
                    repository.markSentBatch(toLocalMessages(originals), nextRetryAt);
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
            // 兜底：拦截器/路由/序列化等任何未预期异常都在这里收敛，不能让 inFlight 泄漏。
            publishFailed(batch, Instant.now().plus(config.retryAfter()), ex);
            return 0;
        } finally {
            // 无论成功、失败、异常，都必须释放 inFlight，否则 Scanner 永远跳过这些消息。
            for (OutboxRecord record : batch) {
                inFlight.remove(record.msgId());
            }
        }
    }

    /**
     * 统一处理发送失败：通知拦截器，并在 MySQL 中递增重试次数。
     */
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
            // 状态回写失败也不能让发送线程退出；下一轮扫描会再次处理。
            log.warn("mark failed batch failed, size={}", records.size(), markError);
        }
    }

    private List<OutboxRecord> originalsOf(
            List<OutboxRecord> records,
            Map<String, OutboxRecord> originalByMsgId) {
        List<OutboxRecord> originals = new ArrayList<>(records.size());
        for (OutboxRecord record : records) {
            OutboxRecord original = originalByMsgId.get(record.msgId());
            if (original == null) {
                throw new IllegalStateException(
                        "interceptor changed msgId, msgId=" + record.msgId());
            }
            originals.add(original);
        }
        return originals;
    }

    private List<LocalMessage> toLocalMessages(List<OutboxRecord> records) {
        List<LocalMessage> messages = new ArrayList<>(records.size());
        for (OutboxRecord record : records) {
            messages.add(record.toLocalMessage());
        }
        return messages;
    }
}
