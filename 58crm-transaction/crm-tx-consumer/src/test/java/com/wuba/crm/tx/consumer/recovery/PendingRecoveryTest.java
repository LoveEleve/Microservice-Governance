package com.wuba.crm.tx.consumer.recovery;

import com.wuba.crm.tx.consumer.ack.StreamAcknowledger;
import com.wuba.crm.tx.consumer.domain.ConsumeStatus;
import com.wuba.crm.tx.consumer.domain.ConsumerRecord;
import com.wuba.crm.tx.consumer.persistence.ConsumerRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingRecoveryTest {

    private static final String STREAM = "tx:stream:lead";
    private static final String GROUP = "tx-group-lead";

    @Test
    void consumedPending_isOnlyAcknowledged() {
        Fixture fixture = fixture(record(ConsumeStatus.CONSUMED, null));

        fixture.recovery.recoverOnce();

        verify(fixture.acknowledger).acknowledge(STREAM, GROUP, "1-0");
        verify(fixture.repository, never()).markExpiredProcessingReady(anyString(), any(Instant.class));
    }

    @Test
    void expiredProcessing_isResetToReady() {
        Fixture fixture = fixture(record(ConsumeStatus.PROCESSING, Instant.now().minusSeconds(1)));

        fixture.recovery.recoverOnce();

        verify(fixture.repository).markExpiredProcessingReady(
                eq("m-1"), any(Instant.class));
    }

    @Test
    void activeProcessing_isSkipped() {
        Fixture fixture = fixture(record(ConsumeStatus.PROCESSING, Instant.now().plusSeconds(60)));

        fixture.recovery.recoverOnce();

        verify(fixture.repository, never()).markExpiredProcessingReady(
                anyString(), any(Instant.class));
        verify(fixture.acknowledger, never()).acknowledge(anyString(), anyString(), any(String[].class));
    }

    @Test
    void missingRecord_isClaimedAndPersisted() {
        Fixture fixture = fixture(null);
        MapRecord<String, Object, Object> claimed = MapRecord.create(
                        STREAM, Map.<Object, Object>of(
                                "msgId", "m-1", "bizCode", "lead", "bizId", "lead-1",
                                "eventType", "LEAD_ASSIGNED", "payload", "{}"))
                .withId(RecordId.of("1-0"));
        when(fixture.operations.claim(
                anyString(), anyString(), anyString(), any(Duration.class),
                any(RecordId.class))).thenReturn(List.of(claimed));

        fixture.recovery.recoverOnce();

        verify(fixture.repository).insertReadyBatch(any(), any(Instant.class));
    }

    private Fixture fixture(ConsumerRecord record) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> operations = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(operations);
        PendingMessage pending = new PendingMessage(
                RecordId.of("1-0"), Consumer.from(GROUP, "consumer-1"),
                Duration.ofSeconds(60), 1);
        when(operations.pending(eq(STREAM), eq(GROUP), any(Range.class), anyLong()))
                .thenReturn(new PendingMessages(GROUP, List.of(pending)));
        ConsumerRecordRepository repository = mock(ConsumerRecordRepository.class);
        when(repository.findByStreamEntryId("1-0")).thenReturn(record);
        StreamAcknowledger acknowledger = mock(StreamAcknowledger.class);
        PendingRecovery recovery = new PendingRecovery(
                redis, STREAM, GROUP, "recovery-1", repository, acknowledger,
                100, Duration.ofSeconds(30));
        return new Fixture(recovery, operations, repository, acknowledger);
    }

    private ConsumerRecord record(ConsumeStatus status, Instant leaseUntil) {
        return new ConsumerRecord(
                "m-1", "lead", "lead-1", "LEAD_ASSIGNED", "1-0",
                "consumer-1", status, "owner-1", 1, 1,
                leaseUntil, 0, null, "{}", null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    private record Fixture(
            PendingRecovery recovery,
            StreamOperations<String, Object, Object> operations,
            ConsumerRecordRepository repository,
            StreamAcknowledger acknowledger) {
    }
}
