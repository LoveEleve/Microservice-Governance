package com.wuba.crm.tx.outbox;

import java.time.Duration;

/**
 * Outbox 发送端的生产参数。
 *
 * <p>窗口、批次、buffer 容量都必须显式配置；默认值是生产起点，不是魔法常量。</p>
 */
public record OutboxConfig(
        String streamPrefix,
        Duration linger,
        int batchSize,
        int bufferCapacity,
        int scanBatch,
        Duration scanInterval,
        Duration retryAfter) {

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
                1_000,
                Duration.ofMillis(10),
                Duration.ofSeconds(30));
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
