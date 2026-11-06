package com.wuba.crm.tx.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
        int relayed = 0;
        Instant nextRetryAt = Instant.now().plus(retryAfter);

        for (LocalMessage message : candidates) {
            try {
                queue.enqueue(message.bizCode(), QueueEnvelope.from(message));
                repository.markSent(message.bizId(), message.msgId(), nextRetryAt);
                relayed++;
            } catch (RuntimeException ex) {
                log.warn("outbox relay failed, msgId={}, bizId={}",
                        message.msgId(), message.bizId(), ex);
            }
        }
        return relayed;
    }
}
