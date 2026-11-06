package com.wuba.crm.tx.common;

/**
 * 生产方本地消息（Outbox）的状态。
 *
 * <p>生产方负责把消息可靠地写进 MySQL、投递到 Redis Stream。
 * 消费确认由 Stream 的 XACK 和消费方 t_consume_record 维护，
 * 生产方不保存 CONSUMED，也不维护消费租约。</p>
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
    /** 已投递到 Redis Stream，生产方不再维护消费结果。 */
    SENT,
    /** 重投次数超限，等待人工处理。 */
    DEAD
}
