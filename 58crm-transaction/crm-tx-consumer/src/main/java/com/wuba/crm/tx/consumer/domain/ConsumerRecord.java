package com.wuba.crm.tx.consumer.domain;

import java.time.Instant;
import java.util.Objects;

public record ConsumerRecord(
        String msgId,
        String bizCode,
        String bizId,
        String eventType,
        String streamEntryId,
        String consumerId,
        ConsumeStatus status,
        String ownerToken,
        long ownerEpoch,
        int claimCount,
        Instant leaseUntil,
        int retryCount,
        Instant nextRetryAt,
        String payload,
        String payloadRef,
        Instant createdAt,
        Instant updatedAt) {

    public ConsumerRecord {
        requireText(msgId, "msgId");
        requireText(bizCode, "bizCode");
        requireText(bizId, "bizId");
        requireText(eventType, "eventType");
        requireText(streamEntryId, "streamEntryId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (ownerEpoch < 0) {
            throw new IllegalArgumentException("ownerEpoch must not be negative");
        }
        if (claimCount < 0) {
            throw new IllegalArgumentException("claimCount must not be negative");
        }
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
        if (isBlank(payload) && isBlank(payloadRef)) {
            throw new IllegalArgumentException("payload or payloadRef is required");
        }
    }

    public static ConsumerRecord ready(ConsumedMessage message, Instant now) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(now, "now");
        return new ConsumerRecord(
                message.msgId(),
                message.bizCode(),
                message.bizId(),
                message.eventType(),
                message.streamEntryId(),
                null,
                ConsumeStatus.READY,
                null,
                0,
                0,
                null,
                0,
                null,
                message.payload(),
                message.payloadRef(),
                now,
                now);
    }

    private static void requireText(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
