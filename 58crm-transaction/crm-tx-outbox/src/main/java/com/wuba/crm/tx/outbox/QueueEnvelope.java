package com.wuba.crm.tx.outbox;

/**
 * Redis List 中传输的消息信封。
 *
 * <p>只传 {@code msgId} 不够：消息表按 {@code bizId} 分片，消费方必须拿到
 * {@code bizId} 才能按分片读取自己的消费记录和消息内容。正文也随信封传递，
 * 这样消费方可以在本地落消费记录后独立重试，不需要反向访问生产方数据库。</p>
 */
public record QueueEnvelope(
        String bizId,
        String msgId,
        String eventKey,
        String eventType,
        String payload,
        String payloadRef) {

    public QueueEnvelope {
        requireText(bizId, "bizId");
        requireText(msgId, "msgId");
        requireText(eventKey, "eventKey");
        requireText(eventType, "eventType");
        if ((payload == null || payload.isBlank())
                && (payloadRef == null || payloadRef.isBlank())) {
            throw new IllegalArgumentException("payload or payloadRef is required");
        }
    }

    public static QueueEnvelope from(LocalMessage message) {
        return new QueueEnvelope(
                message.bizId(),
                message.msgId(),
                message.eventKey(),
                message.eventType(),
                message.payload(),
                message.payloadRef());
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
