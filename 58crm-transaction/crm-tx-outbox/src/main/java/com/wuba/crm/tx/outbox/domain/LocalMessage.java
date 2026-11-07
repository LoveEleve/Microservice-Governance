package com.wuba.crm.tx.outbox.domain;

import com.wuba.crm.tx.outbox.persistence.LocalMessageEntity;

import com.wuba.crm.tx.common.MessageStatus;

import java.util.Objects;

/**
 * 生产方本地消息（Outbox）的领域视图。
 *
 * <p>数据库实体是 {@link LocalMessageEntity}；这个 record 不参与 ORM 映射，
 * 目的是把消息正本字段以只读形式暴露给 Relay 和测试代码。消费租约不在这个对象里。</p>
 */
public record LocalMessage(
        String msgId,
        String eventKey,
        String bizCode,
        String bizId,
        String eventType,
        String payload,
        String payloadRef,
        MessageStatus status,
        int retryCount,
        String streamEntryId) {

    public LocalMessage {
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
    public LocalMessage(
            String msgId, String eventKey, String bizCode, String bizId,
            String eventType, String payload, String payloadRef,
            MessageStatus status, int retryCount) {
        this(msgId, eventKey, bizCode, bizId, eventType, payload, payloadRef,
                status, retryCount, null);
    }
}
