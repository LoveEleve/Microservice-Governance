package com.wuba.crm.tx.tcc.coordinator.service;

import java.time.Duration;
import java.util.List;

/**
 * TCC 持久化重试策略。
 *
 * <p>调用级短重试不在这里；这里只决定 Scheduler 发现失败后
 * 的退避时间和 DEAD 阈值。</p>
 */
public final class TccRetryPolicy {

    private static final TccRetryPolicy DEFAULT = new TccRetryPolicy(
            5,
            List.of(
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(2),
                    Duration.ofMinutes(10)));

    private final int maxRetries;
    private final List<Duration> backoff;

    /**
     * 创建策略。
     *
     * @param maxRetries 最大持久化重试次数
     * @param backoff    退避序列
     */
    public TccRetryPolicy(int maxRetries, List<Duration> backoff) {
        if (maxRetries <= 0) {
            throw new IllegalArgumentException("maxRetries must be positive");
        }
        if (backoff == null || backoff.isEmpty()) {
            throw new IllegalArgumentException("backoff must not be empty");
        }
        this.maxRetries = maxRetries;
        this.backoff = List.copyOf(backoff);
    }

    /**
     * 默认策略。
     *
     * @return 默认策略
     */
    public static TccRetryPolicy defaults() {
        return DEFAULT;
    }

    /**
     * 最大重试次数。
     *
     * @return 最大次数
     */
    public int maxRetries() {
        return maxRetries;
    }

    /**
     * 按第几次重试取退避。
     *
     * @param retryCount 已重试次数，从 1 开始
     * @return 退避时长
     */
    public Duration delay(int retryCount) {
        int index = Math.max(0, Math.min(retryCount - 1, backoff.size() - 1));
        return backoff.get(index);
    }
}
