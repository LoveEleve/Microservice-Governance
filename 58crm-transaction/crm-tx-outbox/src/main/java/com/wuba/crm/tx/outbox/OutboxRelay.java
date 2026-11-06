package com.wuba.crm.tx.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 MySQL Outbox 中的待投递消息推进到 Redis List。
 *
 * <p>投递成功后只标记 SENT，并设置下一次允许重投的时间。生产方不等待消费
 * ACK；ACK 由消费方批量回传，成功后再推进到 CONSUMED。</p>
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final LocalMessageRepository repository;
    private final RedisListQueue queue;
    private final Duration retryAfter;

    public OutboxRelay(
            LocalMessageRepository repository,
            RedisListQueue queue,
            Duration retryAfter) {
        this.repository = repository;
        this.queue = queue;
        this.retryAfter = retryAfter;
    }

    /**
     * 执行一轮投递。
     *
     * @return 本轮成功推送到 Redis 的消息数
     */
    public int relayOnce(int limit) {
        List<LocalMessage> candidates = repository.findRelayCandidates(limit);
        if (candidates.isEmpty()) {
            return 0;
        }

        Map<String, List<LocalMessage>> byBizCode = new LinkedHashMap<>();
        for (LocalMessage message : candidates) {
            byBizCode.computeIfAbsent(message.bizCode(), ignored -> new ArrayList<>())
                    .add(message);
        }

        int relayed = 0;
        Instant nextRetryAt = Instant.now().plus(retryAfter);
        for (Map.Entry<String, List<LocalMessage>> entry : byBizCode.entrySet()) {
            List<QueueEnvelope> envelopes = entry.getValue().stream()
                    .map(QueueEnvelope::from)
                    .toList();
            try {
                queue.enqueueBatch(entry.getKey(), envelopes);
                repository.markSentBatch(entry.getValue(), nextRetryAt);
                relayed += envelopes.size();
            } catch (RuntimeException ex) {
                log.warn("outbox relay batch failed, bizCode={}, size={}",
                        entry.getKey(), envelopes.size(), ex);
            }
        }
        return relayed;
    }
}
