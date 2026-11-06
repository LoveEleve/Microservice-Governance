package com.wuba.crm.tx.outbox;

import java.util.List;

/**
 * 生产方 Outbox 的持久化操作。
 *
 * <p>Relay 只负责把消息投递到 Redis 并推进到 SENT；CONSUMED 只能由消费方
 * 的批量 ACK 推进。这样生产方表里没有消费租约和 consumer_id。</p>
 */
public interface LocalMessageRepository {

    /**
     * 查询需要投递的消息：INIT，或已 SENT 且到达重投时间。
     */
    List<LocalMessage> findRelayCandidates(int limit);

    /**
     * 标记已投递，并设置下一次允许重投的时间。
     */
    boolean markSent(String bizId, String msgId, java.time.Instant nextRetryAt);

    /**
     * 批量标记已投递。默认逐条降级，生产实现应覆盖为批量更新。
     */
    default int markSentBatch(List<LocalMessage> messages, java.time.Instant nextRetryAt) {
        int affected = 0;
        for (LocalMessage message : messages) {
            if (markSent(message.bizId(), message.msgId(), nextRetryAt)) {
                affected++;
            }
        }
        return affected;
    }

    /**
     * 批量 ACK 消费成功的消息。
     *
     * @return 实际从 SENT 推进到 CONSUMED 的行数
     */
    int ackBatch(List<MessageAck> acks);
}
