package com.wuba.crm.tx.tcc.api;

/**
 * RM 本地 barrier 状态。
 *
 * <p>TRIED 表示 Try 已成功；CONFIRMED/CANCELLED 是终态；SUSPENDED 表示
 * Cancel 先到时留下的空回滚标记，用于拒绝迟到的 Try。</p>
 */
public enum BarrierStatus {
    TRIED,
    CONFIRMED,
    CANCELLED,
    SUSPENDED
}
