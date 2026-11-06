package com.wuba.crm.tx.outbox;

import java.time.Duration;

/**
 * Outbox 发送端的生产参数。
 *
 * <p>窗口、批次、buffer 容量都必须显式配置；默认值是生产起点，不是魔法常量。</p>
 */
public record OutboxConfig(
        // Stream key 前缀，最终 key = prefix + bizCode。
        String streamPrefix,
        // 第一条消息到达后，继续等同业务线消息的时间窗口。
        Duration linger,
        // 单次发送最多多少条（对应 Kafka batch.size 的消息数口径）。
        int batchSize,
        // buffer 最多多少条。
        int bufferCapacity,
        // buffer 最多多少字节，防止大 payload OOM。
        long bufferCapacityBytes,
        // Scanner 每轮从 MySQL 取多少条候选。
        int scanBatch,
        // Scanner 多久跑一轮。
        Duration scanInterval,
        // XADD 失败后的下一次重试间隔。
        Duration retryAfter,
        // XADD/序列化/路由失败的最大重试次数。
        int maxRetries) {

    public OutboxConfig {
        if (streamPrefix == null || streamPrefix.isBlank()) {
            throw new IllegalArgumentException("streamPrefix must not be blank");
        }
        requirePositive(linger, "linger");
        requirePositive(scanInterval, "scanInterval");
        requirePositive(retryAfter, "retryAfter");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (bufferCapacity <= 0) {
            throw new IllegalArgumentException("bufferCapacity must be positive");
        }
        if (bufferCapacityBytes <= 0) {
            throw new IllegalArgumentException("bufferCapacityBytes must be positive");
        }
        if (maxRetries <= 0) {
            throw new IllegalArgumentException("maxRetries must be positive");
        }
        if (scanBatch <= 0) {
            throw new IllegalArgumentException("scanBatch must be positive");
        }
    }

    /**
     * 生产默认值：10ms 窗口、256 条/批、50k buffer、10ms 扫描。
     */
    public static OutboxConfig defaults() {
        return new OutboxConfig(
                "tx:stream:",
                Duration.ofMillis(10),
                256,
                50_000,
                64L * 1024 * 1024,
                1_000,
                Duration.ofMillis(10),
                Duration.ofSeconds(30),
                5);
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
