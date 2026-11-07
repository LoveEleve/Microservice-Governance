package com.wuba.crm.tx.consumer.bootstrap;

import java.time.Duration;

public record ConsumerRuntimeConfig(
        String bizCode,
        String consumerId,
        String streamPrefix,
        String groupPrefix,
        int queueCapacity,
        int persistBatchSize,
        Duration persistPollTimeout,
        int workerBatchSize,
        int workerThreads,
        Duration leaseDuration,
        Duration maxProcessing,
        Duration retryAfter,
        int maxRetries,
        Duration workerIdlePoll,
        Duration leaseRenewAfter,
        int ackBatchSize,
        int ackQueueCapacity,
        Duration ackFlushInterval,
        int recoveryBatchSize,
        Duration recoveryScanInterval,
        int pullCount,
        Duration pullBlock) {

    public ConsumerRuntimeConfig {
        requireText(bizCode, "bizCode");
        requireText(consumerId, "consumerId");
        requireText(streamPrefix, "streamPrefix");
        requireText(groupPrefix, "groupPrefix");
        requirePositive(queueCapacity, "queueCapacity");
        requirePositive(persistBatchSize, "persistBatchSize");
        requirePositive(persistPollTimeout, "persistPollTimeout");
        requirePositive(workerBatchSize, "workerBatchSize");
        requirePositive(workerThreads, "workerThreads");
        requirePositive(leaseDuration, "leaseDuration");
        requirePositive(maxProcessing, "maxProcessing");
        requirePositive(retryAfter, "retryAfter");
        requirePositive(maxRetries, "maxRetries");
        requirePositive(workerIdlePoll, "workerIdlePoll");
        requirePositive(leaseRenewAfter, "leaseRenewAfter");
        requirePositive(ackBatchSize, "ackBatchSize");
        requirePositive(ackQueueCapacity, "ackQueueCapacity");
        requirePositive(ackFlushInterval, "ackFlushInterval");
        requirePositive(recoveryBatchSize, "recoveryBatchSize");
        requirePositive(recoveryScanInterval, "recoveryScanInterval");
        requirePositive(pullCount, "pullCount");
        requirePositive(pullBlock, "pullBlock");
    }

    public static ConsumerRuntimeConfig defaults(String bizCode, String consumerId) {
        return new ConsumerRuntimeConfig(
                bizCode,
                consumerId,
                "tx:stream:",
                "tx-group-",
                10_000,
                500,
                Duration.ofMillis(100),
                200,
                8,
                Duration.ofSeconds(60),
                Duration.ofMinutes(5),
                Duration.ofSeconds(30),
                3,
                Duration.ofMillis(10),
                Duration.ofSeconds(20),
                200,
                100_000,
                Duration.ofMillis(100),
                100,
                Duration.ofSeconds(30),
                500,
                Duration.ofSeconds(1));
    }

    public String stream() {
        return streamPrefix + bizCode;
    }

    public String group() {
        return groupPrefix + bizCode;
    }

    private static void requirePositive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
