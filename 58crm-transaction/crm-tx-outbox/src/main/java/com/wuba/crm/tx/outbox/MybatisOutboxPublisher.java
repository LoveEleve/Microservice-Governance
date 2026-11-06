package com.wuba.crm.tx.outbox;

import com.wuba.crm.tx.common.MessageStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 基于 MyBatis-Plus 的 Outbox 发布器。
 *
 * <p>它只做一件事：把事件写成 {@code t_local_message} 的 INIT 记录。
 * 真正的 Redis 投递由后续 Relay 完成，因此发布动作不需要连接 Redis，
 * 也不会因为 Redis 抖动阻塞业务事务。</p>
 */
public class MybatisOutboxPublisher implements OutboxPublisher {

    private final LocalMessageMapper localMessageMapper;

    public MybatisOutboxPublisher(LocalMessageMapper localMessageMapper) {
        this.localMessageMapper = localMessageMapper;
    }

    /**
     * 强制要求调用方已经开启本地事务。
     *
     * <p>如果业务方忘记加事务，Spring 会直接抛异常，而不是悄悄写成独立事务。</p>
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String publish(BusinessEvent event) {
        String msgId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        LocalMessageEntity entity = new LocalMessageEntity();
        entity.setMsgId(msgId);
        entity.setEventKey(event.eventKey());
        entity.setBizCode(event.bizCode());
        entity.setBizId(event.bizId());
        entity.setEventType(event.eventType());
        entity.setPayload(event.payload());
        entity.setStatus(MessageStatus.INIT.name());
        entity.setRetryCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        localMessageMapper.insert(entity);
        return msgId;
    }
}
