package com.wuba.crm.tx.outbox;

import java.util.List;

/**
 * 生产方 Outbox 的持久化操作。
 *
 * <p>发送端只负责把消息投递到 Redis Stream 并推进到 SENT；
 * 消费确认由 Stream XACK 和消费方 t_consume_record 维护。
 * 生产方表里没有消费租约和 consumer_id。</p>
 */
public interface LocalMessageRepository {

    /**
     * 查询需要投递的消息：只查 INIT，且到达允许重试时间。
     *
     * <p>SENT 是生产方终态；Stream 已接收的消息不能再按超时重发。</p>
     */
    List<LocalMessage> findRelayCandidates(int limit);

    /**
     * 批量标记 XADD 成功。
     */
    int markSentBatch(List<LocalMessage> messages, java.time.Instant nextRetryAt);

    /**
     * 批量标记 XADD/序列化/路由失败，递增重试并设置下一次时间。
     *
     * @return 实际更新的行数
     */
    int markFailedBatch(List<LocalMessage> messages, java.time.Instant nextRetryAt, int maxRetries);

}