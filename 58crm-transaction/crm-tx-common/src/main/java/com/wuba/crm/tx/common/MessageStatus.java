package com.wuba.crm.tx.common;

/**
 * 本地消息的生命周期。
 *
 * <p>状态只能按以下方向迁移，重试和人工处理是仅有的回退路径：</p>
 *
 * <pre>
 * INIT -> SENT -> PROCESSING -> CONSUMED
 *                    |
 *                    +-> SENT（重试）
 * 重试超限 -> DEAD
 * </pre>
 */
public enum MessageStatus {
    /** 已写入本地消息表，但尚未投递到 Redis。 */
    INIT,
    /** 已投递到 Redis List，等待消费者领取。 */
    SENT,
    /** 消费者已领取，正在执行业务处理。 */
    PROCESSING,
    /** 业务处理成功，消息已完成。 */
    CONSUMED,
    /** 重试次数超限，等待人工处理。 */
    DEAD
}
