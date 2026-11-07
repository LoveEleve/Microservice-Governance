package com.wuba.crm.tx.consumer.domain;

import java.time.Instant;
import java.util.Objects;

public record ClaimRequest(
        String bizCode,
        String consumerId,
        String ownerToken,
        int limit,
        Instant now,
        Instant leaseUntil) {

    private static final int MAX_LIMIT = 1000;

    public ClaimRequest {
        requireText(bizCode, "bizCode");
        requireText(consumerId, "consumerId");
        requireText(ownerToken, "ownerToken");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        if (!leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("leaseUntil must be after now");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
