package com.wuba.crm.tx.outbox.persistence;

import com.wuba.crm.tx.outbox.api.BusinessEvent;
import com.wuba.crm.tx.outbox.api.OutboxPublisher;

import com.wuba.crm.tx.common.MessageStatus;
import com.wuba.crm.tx.outbox.api.BusinessEvent;
import com.wuba.crm.tx.outbox.api.OutboxPublisher;
import com.wuba.crm.tx.outbox.config.BizCodeRegistry;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 基于 MyBatis-Plus 的 Outbox 发布器。
 *
 * <p>它只做一件事：把事件写成 {@code t_local_message} 的 INIT 记录。
 * Redis Stream 投递由后续 Scanner/Sender 完成，因此发布动作不连接 Redis，
 * 也不会因为 Redis 抖动阻塞业务事务。</p>
 */
public class MybatisOutboxPublisher implements OutboxPublisher {

    private final LocalMessageMapper localMessageMapper;
    private final BizCodeRegistry bizCodeRegistry;

    public MybatisOutboxPublisher(
            LocalMessageMapper localMessageMapper,
            BizCodeRegistry bizCodeRegistry) {
        this.localMessageMapper = localMessageMapper;
        this.bizCodeRegistry = bizCodeRegistry;
    }

    /**
     * 强制要求调用方已经开启本地事务。
     *
     * <p>如果业务方忘记加事务，Spring 会直接抛异常，而不是悄悄写成独立事务。</p>
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String publish(BusinessEvent event) {
        // 先在事务内校验业务线，未知 bizCode 不允许写入消息表。
        bizCodeRegistry.require(event.bizCode());

        // msgId 是消息记录身份；eventKey 才是业务事件幂等键，两者不能混用。
        String msgId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // 只插入本地消息表，不在这里写 Redis/Stream。
        LocalMessageEntity entity = new LocalMessageEntity();
        entity.setMsgId(msgId);
        entity.setEventKey(event.eventKey());
        entity.setBizCode(event.bizCode());
        entity.setBizId(event.bizId());
        entity.setEventType(event.eventType());
        entity.setPayload(event.payload());
        // 初始状态必须是 INIT：消息还没进入 Stream。
        entity.setStatus(MessageStatus.INIT.name());
        entity.setRetryCount(0);
        // 首次投递也使用 next_retry_at，Scanner 可以用单列范围+排序，避免 IS NULL OR。
        entity.setNextRetryAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        localMessageMapper.insert(entity);
        return msgId;
    }
}
