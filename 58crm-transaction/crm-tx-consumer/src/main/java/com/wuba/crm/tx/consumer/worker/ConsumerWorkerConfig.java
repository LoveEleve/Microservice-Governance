package com.wuba.crm.tx.consumer.worker;

import java.time.Duration;

public record ConsumerWorkerConfig(
        String bizCode,
        String stream,
        String group,
        String consumerId,
        int batchSize,
        Duration leaseDuration,
        Duration maxProcessing,
        Duration retryAfter,
        int maxRetries,
        Duration idlePoll) {

    public ConsumerWorkerConfig {
        requireText(bizCode, "bizCode");
        requireText(stream, "stream");
        requireText(group, "group");
        requireText(consumerId, "consumerId");
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        requirePositive(leaseDuration, "leaseDuration");
        requirePositive(maxProcessing, "maxProcessing");
        requirePositive(retryAfter, "retryAfter");
        requirePositive(idlePoll, "idlePoll");
        if (maxRetries < 1) {
            throw new IllegalArgumentException("maxRetries must be positive");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
