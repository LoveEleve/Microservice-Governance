package com.wuba.crm.tx.consumer.lease;

import java.time.Instant;
import java.util.Objects;

public record LeaseTask(
        String msgId,
        String bizCode,
        String consumerId,
        String ownerToken,
        long ownerEpoch,
        Instant maxDeadline) {

    public LeaseTask {
        requireText(msgId, "msgId");
        requireText(bizCode, "bizCode");
        requireText(consumerId, "consumerId");
        requireText(ownerToken, "ownerToken");
        Objects.requireNonNull(maxDeadline, "maxDeadline");
        if (ownerEpoch < 1) {
            throw new IllegalArgumentException("ownerEpoch must be positive");
        }
    }

    public String key() {
        return msgId + ":" + ownerToken;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(maxDeadline);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
