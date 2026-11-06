package com.wuba.crm.tx.common;

/**
 * 生产方本地消息（Outbox）的状态。
 *
 * <p>生产方负责把消息可靠地写进 MySQL、投递到 Redis，并接收消费方的批量 ACK。
 * 生产方不维护消费租约，因此这里没有 PROCESSING。</p>
 *
 * <pre>
 * INIT -> SENT -> CONSUMED
 *           |
 *           +-> SENT（超时重投）
 *           +-> DEAD（重投超限）
 * </pre>
 */
public enum MessageStatus {
    /** 已写入本地消息表，尚未投递到 Redis。 */
    INIT,
    /** 已投递到 Redis List，等待消费方批量 ACK。 */
    SENT,
    /** 消费方已批量 ACK，生产方不再重投。 */
    CONSUMED,
    /** 重投次数超限，等待人工处理。 */
    DEAD
}
