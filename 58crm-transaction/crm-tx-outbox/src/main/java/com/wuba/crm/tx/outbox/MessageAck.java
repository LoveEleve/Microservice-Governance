package com.wuba.crm.tx.outbox;

/**
 * 批量 ACK 中的单条回执。
 *
 * <p>{@code bizId} 不能省：消息表按 biz_id 分片，只有 msgId 无法路由到正确分片。</p>
 */
public record MessageAck(String bizId, String msgId) {

    public MessageAck {
        if (bizId == null || bizId.isBlank()) {
            throw new IllegalArgumentException("bizId must not be blank");
        }
        if (msgId == null || msgId.isBlank()) {
            throw new IllegalArgumentException("msgId must not be blank");
        }
    }
}
