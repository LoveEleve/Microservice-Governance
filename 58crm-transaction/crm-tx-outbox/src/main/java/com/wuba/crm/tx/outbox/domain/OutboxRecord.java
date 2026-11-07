package com.wuba.crm.tx.outbox.domain;

import com.wuba.crm.tx.common.MessageStatus;

import java.util.Objects;

/**
 * 发送线程处理的不可变消息记录。
 *
 * <p>它从 {@link LocalMessage} 转换而来，保留消息正本字段和当前状态。
 * 拦截器可以返回新的 record，但不直接修改数据库实体。</p>
 */
public record OutboxRecord(
        // 消息身份字段：拦截器不允许修改这些字段。
        String msgId,
        String eventKey,
        String bizCode,
        String bizId,
        String eventType,
        String payload,
        String payloadRef,
        // 发送时只应出现 INIT；SENT 是发送成功后的生产方终态。
        MessageStatus status,
        int retryCount,
        String streamEntryId) {

    public OutboxRecord {
        Objects.requireNonNull(msgId, "msgId");
        Objects.requireNonNull(eventKey, "eventKey");
        Objects.requireNonNull(bizCode, "bizCode");
        Objects.requireNonNull(bizId, "bizId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(status, "status");
        if ((payload == null || payload.isBlank())
                && (payloadRef == null || payloadRef.isBlank())) {
            throw new IllegalArgumentException("payload or payloadRef is required");
        }
    }

    /**
     * 兼容旧测试/调用方的构造器，Stream entry ID 为空。
     */
    public OutboxRecord(
            String msgId, String eventKey, String bizCode, String bizId,
            String eventType, String payload, String payloadRef,
            MessageStatus status, int retryCount) {
        this(msgId, eventKey, bizCode, bizId, eventType, payload, payloadRef,
                status, retryCount, null);
    }

    public static OutboxRecord from(LocalMessage message) {
        return new OutboxRecord(
                message.msgId(),
                message.eventKey(),
                message.bizCode(),
                message.bizId(),
                message.eventType(),
                message.payload(),
                message.payloadRef(),
                message.status(),
                message.retryCount(),
                message.streamEntryId());
    }

    public LocalMessage toLocalMessage() {
        return new LocalMessage(
                msgId,
                eventKey,
                bizCode,
                bizId,
                eventType,
                payload,
                payloadRef,
                status,
                retryCount,
                streamEntryId);
    }
}
