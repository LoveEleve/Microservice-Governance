package com.wuba.crm.tx.consumer.persistence;

import com.wuba.crm.tx.consumer.domain.ClaimRequest;
import com.wuba.crm.tx.consumer.domain.ConsumeStatus;
import com.wuba.crm.tx.consumer.domain.ConsumedMessage;
import com.wuba.crm.tx.consumer.domain.ConsumerRecord;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MybatisConsumerRecordRepository implements ConsumerRecordRepository {

    private static final int MAX_BATCH_SIZE = 1000;

    private final ConsumerRecordMapper mapper;

    public MybatisConsumerRecordRepository(ConsumerRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insertReadyBatch(List<ConsumedMessage> messages, Instant now) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        Map<String, ConsumerRecordEntity> unique = new LinkedHashMap<>();
        for (ConsumedMessage message : messages) {
            ConsumerRecord record = ConsumerRecord.ready(message, now);
            unique.putIfAbsent(record.msgId(), toEntity(record));
        }
        if (unique.isEmpty()) {
            return 0;
        }
        List<String> existing = mapper.findExistingIds(new ArrayList<>(unique.keySet()));
        for (String msgId : existing) {
            unique.remove(msgId);
        }
        if (unique.isEmpty()) {
            return 0;
        }
        return mapper.insertReadyBatch(new ArrayList<>(unique.values()));
    }

    @Override
    public List<ConsumerRecord> claimReadyBatch(ClaimRequest request) {
        List<String> ids = mapper.selectReadyIds(
                request.bizCode(),
                toLocalDateTime(request.now()),
                request.limit());
        if (ids.isEmpty()) {
            return List.of();
        }
        int claimed = 0;
        for (String id : ids) {
            claimed += mapper.claimReadyById(
                    id,
                    request.consumerId(),
                    request.ownerToken(),
                    toLocalDateTime(request.now()),
                    toLocalDateTime(request.leaseUntil()));
        }
        if (claimed == 0) {
            return List.of();
        }
        List<ConsumerRecord> records = new ArrayList<>(claimed);
        for (ConsumerRecordEntity entity : mapper.findByOwnerToken(request.ownerToken())) {
            records.add(toDomain(entity));
        }
        return records;
    }

    @Override
    public int renewLease(
            String msgId,
            String ownerToken,
            long ownerEpoch,
            Instant leaseUntil,
            Instant now) {
        return mapper.renewLease(
                msgId,
                ownerToken,
                ownerEpoch,
                toLocalDateTime(leaseUntil),
                toLocalDateTime(now));
    }

    @Override
    public int renewLeaseBatch(
            List<String> msgIds,
            String ownerToken,
            long ownerEpoch,
            Instant leaseUntil,
            Instant now) {
        if (msgIds == null || msgIds.isEmpty()) {
            return 0;
        }
        return mapper.renewLeaseBatch(
                msgIds,
                ownerToken,
                ownerEpoch,
                toLocalDateTime(leaseUntil),
                toLocalDateTime(now));
    }

    @Override
    public ConsumerRecord findByStreamEntryId(String streamEntryId) {
        ConsumerRecordEntity entity = mapper.findByStreamEntryId(streamEntryId);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public int markExpiredProcessingReady(String msgId, Instant now) {
        return mapper.markExpiredProcessingReady(msgId, toLocalDateTime(now));
    }

    @Override
    public int markConsumed(String msgId, String ownerToken, long ownerEpoch, Instant now) {
        return mapper.markConsumed(msgId, ownerToken, ownerEpoch, toLocalDateTime(now));
    }

    @Override
    public int markRetry(
            String msgId,
            String ownerToken,
            long ownerEpoch,
            Instant nextRetryAt,
            int maxRetries,
            Instant now) {
        return mapper.markRetry(
                msgId,
                ownerToken,
                ownerEpoch,
                toLocalDateTime(nextRetryAt),
                maxRetries,
                toLocalDateTime(now));
    }

    @Override
    public int markDead(String msgId, String ownerToken, long ownerEpoch, Instant now) {
        return mapper.markDead(msgId, ownerToken, ownerEpoch, toLocalDateTime(now));
    }

    @Override
    public List<ConsumerRecord> findExpiredLeases(Instant now, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_BATCH_SIZE));
        List<ConsumerRecord> records = new ArrayList<>();
        for (ConsumerRecordEntity entity : mapper.findExpiredLeases(
                toLocalDateTime(now), safeLimit)) {
            records.add(toDomain(entity));
        }
        return records;
    }

    private ConsumerRecordEntity toEntity(ConsumerRecord record) {
        ConsumerRecordEntity entity = new ConsumerRecordEntity();
        entity.setMsgId(record.msgId());
        entity.setEventKey(record.eventKey());
        entity.setBizCode(record.bizCode());
        entity.setBizId(record.bizId());
        entity.setEventType(record.eventType());
        entity.setStreamEntryId(record.streamEntryId());
        entity.setConsumerId(record.consumerId());
        entity.setStatus(record.status().name());
        entity.setOwnerToken(record.ownerToken());
        entity.setOwnerEpoch(record.ownerEpoch());
        entity.setClaimCount(record.claimCount());
        entity.setLeaseUntil(toLocalDateTime(record.leaseUntil()));
        entity.setRetryCount(record.retryCount());
        entity.setNextRetryAt(toLocalDateTime(record.nextRetryAt()));
        entity.setPayload(record.payload());
        entity.setPayloadRef(record.payloadRef());
        entity.setCreatedAt(toLocalDateTime(record.createdAt()));
        entity.setUpdatedAt(toLocalDateTime(record.updatedAt()));
        return entity;
    }

    private ConsumerRecord toDomain(ConsumerRecordEntity entity) {
        return new ConsumerRecord(
                entity.getMsgId(),
                entity.getEventKey(),
                entity.getBizCode(),
                entity.getBizId(),
                entity.getEventType(),
                entity.getStreamEntryId(),
                entity.getConsumerId(),
                ConsumeStatus.valueOf(entity.getStatus()),
                entity.getOwnerToken(),
                entity.getOwnerEpoch() == null ? 0 : entity.getOwnerEpoch(),
                entity.getClaimCount() == null ? 0 : entity.getClaimCount(),
                toInstant(entity.getLeaseUntil()),
                entity.getRetryCount() == null ? 0 : entity.getRetryCount(),
                toInstant(entity.getNextRetryAt()),
                entity.getPayload(),
                entity.getPayloadRef(),
                toInstant(entity.getCreatedAt()),
                toInstant(entity.getUpdatedAt()));
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant toInstant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC);
    }
}
