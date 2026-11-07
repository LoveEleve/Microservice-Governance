package com.wuba.crm.tx.consumer.persistence;

import com.wuba.crm.tx.consumer.domain.ClaimRequest;
import com.wuba.crm.tx.consumer.domain.ConsumedMessage;
import com.wuba.crm.tx.consumer.domain.ConsumerRecord;

import java.time.Instant;
import java.util.List;

public interface ConsumerRecordRepository {

    int insertReadyBatch(List<ConsumedMessage> messages, Instant now);

    List<ConsumerRecord> claimReadyBatch(ClaimRequest request);

    int renewLease(
            String msgId,
            String ownerToken,
            long ownerEpoch,
            Instant leaseUntil,
            Instant now);

    int renewLeaseBatch(
            List<String> msgIds,
            String ownerToken,
            long ownerEpoch,
            Instant leaseUntil,
            Instant now);

    ConsumerRecord findByStreamEntryId(String streamEntryId);

    int markExpiredProcessingReady(String msgId, Instant now);

    int markConsumed(String msgId, String ownerToken, long ownerEpoch, Instant now);

    int markRetry(
            String msgId,
            String ownerToken,
            long ownerEpoch,
            Instant nextRetryAt,
            int maxRetries,
            Instant now);

    int markDead(String msgId, String ownerToken, long ownerEpoch, Instant now);

    List<ConsumerRecord> findExpiredLeases(Instant now, int limit);
}
