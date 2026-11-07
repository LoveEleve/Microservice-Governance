package com.wuba.crm.tx.outbox.persistence;

import com.wuba.crm.tx.outbox.domain.LocalMessage;

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
     * 批量标记投递成功并保存 Redis Stream entry ID。
     *
     * <p>默认忽略 entryIds，兼容旧的测试/自定义实现；生产实现会保存。</p>
     */
    default int markSentBatch(
            List<LocalMessage> messages,
            java.time.Instant nextRetryAt,
            java.util.Map<String, String> entryIds) {
        return markSentBatch(messages, nextRetryAt);
    }

    /**
     * 对账发现消费方已 CONSUMED，批量推进 SENT -> DONE。
     * 默认空实现，生产实现覆盖。
     */
    default int markDoneBatch(List<LocalMessage> messages) {
        return 0;
    }

    /**
     * 查询需要参与 Redis 丢失对账的 SENT 消息。
     * 默认无候选，生产实现覆盖。
     */
    default List<LocalMessage> findForReconcile(java.time.Instant before, int limit) {
        return List.of();
    }

    /**
     * 批量标记 XADD/序列化/路由失败，递增重试并设置下一次时间。
     *
     * @return 实际更新的行数
     */
    int markFailedBatch(List<LocalMessage> messages, java.time.Instant nextRetryAt, int maxRetries);

}
