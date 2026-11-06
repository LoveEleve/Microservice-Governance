package com.wuba.crm.tx.outbox;

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
        int retryCount) {

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
}
