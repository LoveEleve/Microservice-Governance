package com.wuba.crm.tx.outbox.api;

import java.util.Objects;

/**
 * 业务方要发布的一条领域事件。
 *
 * <p>这个对象只描述“发生了什么”，不包含投递状态。投递状态由
 * {@code t_local_message} 维护。业务方必须在本地事务内调用
 * {@link OutboxPublisher#publish(BusinessEvent)}，保证业务数据和消息同时提交。</p>
 *
 * @param eventKey  全局唯一事件键，建议包含业务主键和版本/分配 ID
 * @param bizCode   业务编码，同时作为 Redis Stream 的路由键
 * @param bizId     业务单号，消息表分片键
 * @param eventType 事件类型，例如 LEAD_ASSIGNED
 * @param payload   事件正文；大消息应由业务方转成 payloadRef 后传空正文
 */
public record BusinessEvent(
        String eventKey,
        String bizCode,
        String bizId,
        String eventType,
        String payload) {

    public BusinessEvent {
        requireText(eventKey, "eventKey");
        requireText(bizCode, "bizCode");
        requireText(bizId, "bizId");
        requireText(eventType, "eventType");
        Objects.requireNonNull(payload, "payload");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
