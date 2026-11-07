package com.wuba.crm.tx.consumer.domain;

public record ConsumedMessage(
        String msgId,
        String eventKey,
        String bizCode,
        String bizId,
        String eventType,
        String streamEntryId,
        String payload,
        String payloadRef) {

    public ConsumedMessage {
        requireText(msgId, "msgId");
        requireText(bizCode, "bizCode");
        requireText(bizId, "bizId");
        requireText(eventType, "eventType");
        requireText(streamEntryId, "streamEntryId");
        if (isBlank(payload) && isBlank(payloadRef)) {
            throw new IllegalArgumentException("payload or payloadRef is required");
        }
    }

    public ConsumedMessage(
            String msgId,
            String bizCode,
            String bizId,
            String eventType,
            String streamEntryId,
            String payload,
            String payloadRef) {
        this(msgId, msgId, bizCode, bizId, eventType, streamEntryId, payload, payloadRef);
    }

    public String idempotencyKey() {
        return isBlank(eventKey) ? msgId : eventKey;
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
