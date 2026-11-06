package com.wuba.crm.tx.common;

/**
 * 生产方本地消息（Outbox）的状态。
 *
 * <p>生产方只负责把消息可靠地写进 MySQL、投递到 Redis，并允许重复投递。
 * 它不知道消费者是否处理成功，因此这里没有 PROCESSING/CONSUMED。</p>
 *
 * <pre>
 * INIT -> SENT -> DEAD
 *           |
 *           +-> SENT（超时重投）
 * </pre>
 */
public enum MessageStatus {
    /** 已写入本地消息表，尚未投递到 Redis。 */
    INIT,
    /** 已投递到 Redis List；生产方不确认消费结果。 */
    SENT,
    /** 重投次数超限，等待人工处理。 */
    DEAD
}
