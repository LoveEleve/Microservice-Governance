package com.wuba.crm.tx.outbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wuba.crm.tx.common.MessageStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 MyBatis-Plus 的生产方 Outbox 仓储。
 *
 * <p>所有更新都带状态条件，保证 Relay 重投和 ACK 重复调用不会把终态改回去。</p>
 */
public class MybatisLocalMessageRepository implements LocalMessageRepository {

    private static final int MAX_BATCH_SIZE = 1000;

    private final LocalMessageMapper localMessageMapper;

    public MybatisLocalMessageRepository(LocalMessageMapper localMessageMapper) {
        this.localMessageMapper = localMessageMapper;
    }

    @Override
    public List<LocalMessage> findRelayCandidates(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_BATCH_SIZE));
        LocalDateTime now = LocalDateTime.now();

        LambdaQueryWrapper<LocalMessageEntity> query = Wrappers.lambdaQuery();
        query.and(wrapper -> wrapper
                        .eq(LocalMessageEntity::getStatus, MessageStatus.INIT.name())
                        .or(sent -> sent
                                .eq(LocalMessageEntity::getStatus, MessageStatus.SENT.name())
                                .le(LocalMessageEntity::getNextRetryAt, now)))
                .orderByAsc(LocalMessageEntity::getCreatedAt)
                .last("LIMIT " + safeLimit);

        List<LocalMessage> messages = new ArrayList<>();
        for (LocalMessageEntity entity : localMessageMapper.selectList(query)) {
            messages.add(toDomain(entity));
        }
        return messages;
    }

    @Override
    public boolean markSent(String bizId, String msgId, Instant nextRetryAt) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<LocalMessageEntity> update = Wrappers.lambdaUpdate();
        update.eq(LocalMessageEntity::getBizId, bizId)
                .eq(LocalMessageEntity::getMsgId, msgId)
                .in(LocalMessageEntity::getStatus,
                        MessageStatus.INIT.name(),
                        MessageStatus.SENT.name())
                .set(LocalMessageEntity::getStatus, MessageStatus.SENT.name())
                .set(LocalMessageEntity::getNextRetryAt, toLocalDateTime(nextRetryAt))
                .set(LocalMessageEntity::getUpdatedAt, now);
        return localMessageMapper.update(null, update) > 0;
    }

    @Override
    public int ackBatch(List<MessageAck> acks) {
        if (acks == null || acks.isEmpty()) {
            return 0;
        }

        Map<String, List<String>> byBizId = new LinkedHashMap<>();
        for (MessageAck ack : acks) {
            byBizId.computeIfAbsent(ack.bizId(), ignored -> new ArrayList<>())
                    .add(ack.msgId());
        }

        LocalDateTime now = LocalDateTime.now();
        int affected = 0;
        for (Map.Entry<String, List<String>> entry : byBizId.entrySet()) {
            LambdaUpdateWrapper<LocalMessageEntity> update = Wrappers.lambdaUpdate();
            update.eq(LocalMessageEntity::getBizId, entry.getKey())
                    .in(LocalMessageEntity::getMsgId, entry.getValue())
                    .eq(LocalMessageEntity::getStatus, MessageStatus.SENT.name())
                    .set(LocalMessageEntity::getStatus, MessageStatus.CONSUMED.name())
                    .set(LocalMessageEntity::getUpdatedAt, now);
            affected += localMessageMapper.update(null, update);
        }
        return affected;
    }

    private LocalMessage toDomain(LocalMessageEntity entity) {
        return new LocalMessage(
                entity.getMsgId(),
                entity.getEventKey(),
                entity.getBizCode(),
                entity.getBizId(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getPayloadRef(),
                MessageStatus.valueOf(entity.getStatus()),
                entity.getRetryCount() == null ? 0 : entity.getRetryCount());
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
