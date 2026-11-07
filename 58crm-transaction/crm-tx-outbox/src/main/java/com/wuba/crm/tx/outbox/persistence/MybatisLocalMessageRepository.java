package com.wuba.crm.tx.outbox.persistence;

import com.wuba.crm.tx.outbox.domain.LocalMessage;

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

    /**
     * 只查 INIT；SENT 已经写入 Stream，不需要生产方再猜消费结果。
     * next_retry_at 为空表示首次投递，到期时间用于失败退避。
     */
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
        return markSentBatch(messages, nextRetryAt, java.util.Map.of());
    }

    @Override
    public int markSentBatch(
            List<LocalMessage> messages,
            Instant nextRetryAt,
            java.util.Map<String, String> entryIds) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        // 按 biz_id 分组，保证批量 UPDATE 能命中分片键，不会全路由扫描。
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
            // 同一批消息的 entryId 不同，用 CASE 在一个 UPDATE 中写回，避免逐条 UPDATE。
            StringBuilder caseSql = new StringBuilder("stream_entry_id = CASE msg_id ");
            java.util.List<Object> caseArgs = new java.util.ArrayList<>();
            for (LocalMessage message : messages) {
                String entryId = entryIds.get(message.msgId());
                if (entryId != null) {
                    // MyBatis-Plus setSql 使用 {0}/{1} 占位，不是 JDBC 的 ?。
                    int index = caseArgs.size();
                    caseSql.append("WHEN {").append(index).append("} THEN {")
                            .append(index + 1).append("} ");
                    caseArgs.add(message.msgId());
                    caseArgs.add(entryId);
                }
            }
            caseSql.append("ELSE stream_entry_id END");
            if (!caseArgs.isEmpty()) {
                update.setSql(caseSql.toString(), caseArgs.toArray());
            }
            affected += localMessageMapper.update(null, update);
        }
        return affected;
    }

    @Override
    public int markDoneBatch(List<LocalMessage> messages) {
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
                    .eq(LocalMessageEntity::getStatus, MessageStatus.SENT.name())
                    .set(LocalMessageEntity::getStatus, MessageStatus.DONE.name())
                    .set(LocalMessageEntity::getUpdatedAt, now);
            affected += localMessageMapper.update(null, update);
        }
        return affected;
    }

    @Override
    public List<LocalMessage> findForReconcile(Instant before, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_BATCH_SIZE));
        LambdaQueryWrapper<LocalMessageEntity> query = Wrappers.lambdaQuery();
        query.eq(LocalMessageEntity::getStatus, MessageStatus.SENT.name())
                .lt(LocalMessageEntity::getCreatedAt, toLocalDateTime(before))
                .orderByAsc(LocalMessageEntity::getCreatedAt)
                .last("LIMIT " + safeLimit);
        List<LocalMessage> messages = new ArrayList<>();
        for (LocalMessageEntity entity : localMessageMapper.selectList(query)) {
            messages.add(toDomain(entity));
        }
        return messages;
    }

    @Override
    public int markFailedBatch(List<LocalMessage> messages, Instant nextRetryAt, int maxRetries) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        // 失败重试也按 biz_id 分组，保证每条 UPDATE 都带分片键。
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
                entity.getRetryCount() == null ? 0 : entity.getRetryCount(),
                entity.getStreamEntryId());
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
