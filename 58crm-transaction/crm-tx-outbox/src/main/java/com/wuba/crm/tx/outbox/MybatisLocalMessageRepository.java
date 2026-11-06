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
    private static final int DEFAULT_MAX_RETRIES = 5;

    private final LocalMessageMapper localMessageMapper;
    private final int maxRetries;

    public MybatisLocalMessageRepository(LocalMessageMapper localMessageMapper) {
        this(localMessageMapper, DEFAULT_MAX_RETRIES);
    }

    public MybatisLocalMessageRepository(LocalMessageMapper localMessageMapper, int maxRetries) {
        if (maxRetries <= 0) {
            throw new IllegalArgumentException("maxRetries must be positive");
        }
        this.localMessageMapper = localMessageMapper;
        this.maxRetries = maxRetries;
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
    public int markSentBatch(List<LocalMessage> messages, Instant nextRetryAt) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int affected = 0;

        // INIT: 首次投递，不增加重试次数。
        Map<String, List<String>> initialByBizId = new LinkedHashMap<>();
        // SENT: 超时重投，增加 retry_count；达到上限转 DEAD。
        Map<String, List<String>> retryByBizId = new LinkedHashMap<>();
        for (LocalMessage message : messages) {
            Map<String, List<String>> target = message.status() == MessageStatus.INIT
                    ? initialByBizId
                    : retryByBizId;
            target.computeIfAbsent(message.bizId(), ignored -> new ArrayList<>())
                    .add(message.msgId());
        }

        for (Map.Entry<String, List<String>> entry : initialByBizId.entrySet()) {
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

        for (Map.Entry<String, List<String>> entry : retryByBizId.entrySet()) {
            LambdaUpdateWrapper<LocalMessageEntity> update = Wrappers.lambdaUpdate();
            update.eq(LocalMessageEntity::getBizId, entry.getKey())
                    .in(LocalMessageEntity::getMsgId, entry.getValue())
                    .eq(LocalMessageEntity::getStatus, MessageStatus.SENT.name())
                    .setSql("status = CASE WHEN retry_count + 1 >= " + maxRetries
                            + " THEN 'DEAD' ELSE 'SENT' END")
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
