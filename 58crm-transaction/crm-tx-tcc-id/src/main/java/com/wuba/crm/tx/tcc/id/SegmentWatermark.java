package com.wuba.crm.tx.tcc.id;

/**
 * 号段水位快照，用于监控和排障。
 *
 * @param bizType   业务 key
 * @param value     当前游标
 * @param max       当前上界
 * @param step      当前步长
 * @param remaining 当前剩余可发号数量
 * @param nextReady 备用号段是否已就绪
 */
public record SegmentWatermark(
        String bizType,
        long value,
        long max,
        int step,
        long remaining,
        boolean nextReady) {
}
