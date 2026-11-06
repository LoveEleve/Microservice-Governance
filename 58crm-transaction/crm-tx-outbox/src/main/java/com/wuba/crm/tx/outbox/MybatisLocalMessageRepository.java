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
 * <p>所有更新都带状态条件，保证发送重投不会把 DEAD 改回 SENT。</p>
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
        query.eq(LocalMessageEntity::getStatus, MessageStatus.INIT.name())
                .and(wrapper -> wrapper
                        .isNull(LocalMessageEntity::getNextRetryAt)
                        .or(retry -> retry.le(LocalMessageEntity::getNextRetryAt, now)))
                .orderByAsc(LocalMessageEntity::getCreatedAt)
                .last("LIMIT " + safeLimit);

        List<LocalMessage> messages = new ArrayList<>();
        for (LocalMessageEntity entity : localMessageMapper.selectList(query)) {
            messages.add(toDomain(entity));
        }
        return messages;
    }

    @Override
    public int markSentBatch(List<LocalMessage> messages, Instant nextRetryAt) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        Map<String, List<String>> byBizId = new LinkedHashMap<>();
        for (LocalMessage message : messages) {
            if (message.status() != MessageStatus.INIT) {
                continue;
            }
            byBizId.computeIfAbsent(message.bizId(), ignored -> new ArrayList<>())
                    .add(message.msgId());
        }

        LocalDateTime now = LocalDateTime.now();
        int affected = 0;
        for (Map.Entry<String, List<String>> entry : byBizId.entrySet()) {
            LambdaUpdateWrapper<LocalMessageEntity> update = Wrappers.lambdaUpdate();
            update.eq(LocalMessageEntity::getBizId, entry.getKey())
                    .in(LocalMessageEntity::getMsgId, entry.getValue())
                    .eq(LocalMessageEntity::getStatus, MessageStatus.INIT.name())
                    .set(LocalMessageEntity::getStatus, MessageStatus.SENT.name())
                    .set(LocalMessageEntity::getRetryCount, 0)
                    .set(LocalMessageEntity::getNextRetryAt, toLocalDateTime(nextRetryAt))
                    .set(LocalMessageEntity::getUpdatedAt, now);
            affected += localMessageMapper.update(null, update);
        }
        return affected;
    }

    @Override
    public int markFailedBatch(List<LocalMessage> messages, Instant nextRetryAt, int maxRetries) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        Map<String, List<String>> byBizId = new LinkedHashMap<>();
        for (LocalMessage message : messages) {
            byBizId.computeIfAbsent(message.bizId(), ignored -> new ArrayList<>())
                    .add(message.msgId());
        }

        LocalDateTime now = LocalDateTime.now();
        int affected = 0;
        for (Map.Entry<String, List<String>> entry : byBizId.entrySet()) {
            LambdaUpdateWrapper<LocalMessageEntity> update = Wrappers.lambdaUpdate();
            update.eq(LocalMessageEntity::getBizId, entry.getKey())
                    .in(LocalMessageEntity::getMsgId, entry.getValue())
                    .eq(LocalMessageEntity::getStatus, MessageStatus.INIT.name())
                    .setSql("status = CASE WHEN retry_count + 1 >= " + maxRetries
                            + " THEN 'DEAD' ELSE 'INIT' END")
                    .setSql("retry_count = retry_count + 1")
                    .set(LocalMessageEntity::getNextRetryAt, toLocalDateTime(nextRetryAt))
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
