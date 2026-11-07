package com.wuba.crm.tx.consumer.worker;

import com.wuba.crm.tx.consumer.ack.AckBatcher;
import com.wuba.crm.tx.consumer.ack.StreamAcknowledger;
import com.wuba.crm.tx.consumer.domain.ClaimRequest;
import com.wuba.crm.tx.consumer.domain.ConsumeStatus;
import com.wuba.crm.tx.consumer.domain.ConsumerRecord;
import com.wuba.crm.tx.consumer.lease.LeaseRegistrar;
import com.wuba.crm.tx.consumer.lease.LeaseTask;
import com.wuba.crm.tx.consumer.persistence.ConsumerRecordRepository;
import com.wuba.crm.tx.consumer.persistence.IdempotentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumerWorkerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void success_marksConsumedAndQueuesAck() {
        ConsumerRecordRepository repository = mock(ConsumerRecordRepository.class);
        ConsumerRecord record = record();
        when(repository.claimReadyBatch(any(ClaimRequest.class))).thenReturn(List.of(record));
        when(repository.markConsumed(anyString(), anyString(), anyLong(), any(Instant.class)))
                .thenReturn(1);
        HandlerRegistry handlers = new HandlerRegistry();
        AtomicInteger handled = new AtomicInteger();
        handlers.register("lead", "LEAD_ASSIGNED", message -> handled.incrementAndGet());
        RecordingLeaseRegistrar leases = new RecordingLeaseRegistrar();
        AckBatcher ackBatcher = ackBatcher();
        ConsumerWorker worker = new ConsumerWorker(
                config(), repository, idempotent(), handlers, transactions(), leases, ackBatcher,
                Clock.fixed(NOW, ZoneOffset.UTC));

        worker.pollOnce();

        org.junit.jupiter.api.Assertions.assertEquals(1, handled.get());
        verify(repository).markConsumed("m-1", "owner-1", 1, NOW);
        org.junit.jupiter.api.Assertions.assertEquals(1, ackBatcher.size());
        org.junit.jupiter.api.Assertions.assertEquals(1, leases.registered);
        org.junit.jupiter.api.Assertions.assertEquals(1, leases.completed);
    }

    @Test
    void claimedBatch_registersAllLeasesBeforeProcessing() {
        ConsumerRecordRepository repository = mock(ConsumerRecordRepository.class);
        when(repository.claimReadyBatch(any(ClaimRequest.class)))
                .thenReturn(List.of(record("m-1"), record("m-2")));
        when(repository.markConsumed(anyString(), anyString(), anyLong(), any(Instant.class)))
                .thenReturn(1);
        HandlerRegistry handlers = new HandlerRegistry();
        handlers.register("lead", "LEAD_ASSIGNED", message -> {
        });
        RecordingLeaseRegistrar leases = new RecordingLeaseRegistrar();
        ConsumerWorker worker = new ConsumerWorker(
                config(), repository, idempotent(), handlers, transactions(), leases,
                ackBatcher(), Clock.fixed(NOW, ZoneOffset.UTC));

        worker.pollOnce();

        org.junit.jupiter.api.Assertions.assertEquals(2, leases.registered);
        org.junit.jupiter.api.Assertions.assertEquals(2, leases.completed);
    }

    @Test
    void idempotency_usesEventKeyInsteadOfMsgId() {
        ConsumerRecordRepository repository = mock(ConsumerRecordRepository.class);
        IdempotentRepository idempotent = mock(IdempotentRepository.class);
        when(repository.claimReadyBatch(any(ClaimRequest.class)))
                .thenReturn(List.of(recordWithEventKey("m-1", "event-1")));
        when(repository.markConsumed(anyString(), anyString(), anyLong(), any(Instant.class)))
                .thenReturn(1);
        HandlerRegistry handlers = new HandlerRegistry();
        handlers.register("lead", "LEAD_ASSIGNED", message -> {
        });
        ConsumerWorker worker = new ConsumerWorker(
                config(), repository, idempotent, handlers, transactions(),
                new RecordingLeaseRegistrar(), ackBatcher(), Clock.fixed(NOW, ZoneOffset.UTC));

        worker.pollOnce();

        verify(idempotent).tryAcquire("m-1", "event-1", NOW);
    }

    @Test
    void duplicateIdempotentKey_skipsHandlerButCompletesAck() {
        ConsumerRecordRepository repository = mock(ConsumerRecordRepository.class);
        IdempotentRepository idempotent = mock(IdempotentRepository.class);
        when(idempotent.tryAcquire(anyString(), anyString(), any(Instant.class)))
                .thenReturn(false);
        when(repository.claimReadyBatch(any(ClaimRequest.class)))
                .thenReturn(List.of(record()));
        when(repository.markConsumed(anyString(), anyString(), anyLong(), any(Instant.class)))
                .thenReturn(1);
        HandlerRegistry handlers = new HandlerRegistry();
        AtomicInteger handled = new AtomicInteger();
        handlers.register("lead", "LEAD_ASSIGNED", message -> handled.incrementAndGet());
        AckBatcher ackBatcher = ackBatcher();
        ConsumerWorker worker = new ConsumerWorker(
                config(), repository, idempotent, handlers, transactions(),
                new RecordingLeaseRegistrar(), ackBatcher, Clock.fixed(NOW, ZoneOffset.UTC));

        worker.pollOnce();

        org.junit.jupiter.api.Assertions.assertEquals(0, handled.get());
        verify(repository).markConsumed("m-1", "owner-1", 1, NOW);
        org.junit.jupiter.api.Assertions.assertEquals(1, ackBatcher.size());
    }

    @Test
    void handlerFailure_marksRetryAndDoesNotAck() {
        ConsumerRecordRepository repository = mock(ConsumerRecordRepository.class);
        when(repository.claimReadyBatch(any(ClaimRequest.class)))
                .thenReturn(List.of(record()));
        HandlerRegistry handlers = new HandlerRegistry();
        handlers.register("lead", "LEAD_ASSIGNED", message -> {
            throw new IllegalStateException("boom");
        });
        AckBatcher ackBatcher = ackBatcher();
        ConsumerWorker worker = new ConsumerWorker(
                config(), repository, idempotent(), handlers, transactions(),
                new RecordingLeaseRegistrar(), ackBatcher, Clock.fixed(NOW, ZoneOffset.UTC));

        worker.pollOnce();

        verify(repository).markRetry(
                "m-1", "owner-1", 1, NOW.plusSeconds(30), 3, NOW);
        org.junit.jupiter.api.Assertions.assertEquals(0, ackBatcher.size());
    }

    @Test
    void fencingFailure_doesNotAckOrRetry() {
        ConsumerRecordRepository repository = mock(ConsumerRecordRepository.class);
        when(repository.claimReadyBatch(any(ClaimRequest.class)))
                .thenReturn(List.of(record()));
        when(repository.markConsumed(anyString(), anyString(), anyLong(), any(Instant.class)))
                .thenReturn(0);
        HandlerRegistry handlers = new HandlerRegistry();
        handlers.register("lead", "LEAD_ASSIGNED", message -> {
        });
        AckBatcher ackBatcher = ackBatcher();
        ConsumerWorker worker = new ConsumerWorker(
                config(), repository, idempotent(), handlers, transactions(),
                new RecordingLeaseRegistrar(), ackBatcher, Clock.fixed(NOW, ZoneOffset.UTC));

        worker.pollOnce();

        org.junit.jupiter.api.Assertions.assertEquals(0, ackBatcher.size());
        verify(repository, never()).markRetry(
                anyString(), anyString(), anyLong(), any(Instant.class), any(Integer.class),
                any(Instant.class));
    }

    private IdempotentRepository idempotent() {
        IdempotentRepository idempotent = mock(IdempotentRepository.class);
        when(idempotent.tryAcquire(anyString(), anyString(), any(Instant.class)))
                .thenReturn(true);
        return idempotent;
    }

    private ConsumerWorkerConfig config() {
        return new ConsumerWorkerConfig(
                "lead", "tx:stream:lead", "tx-group-lead", "consumer-1",
                10, Duration.ofSeconds(60), Duration.ofMinutes(5),
                Duration.ofSeconds(30), 3, Duration.ofMillis(10));
    }

    private ConsumerRecord record() {
        return record("m-1");
    }

    private ConsumerRecord record(String msgId) {
        return recordWithEventKey(msgId, msgId);
    }

    private ConsumerRecord recordWithEventKey(String msgId, String eventKey) {
        return new ConsumerRecord(
                msgId, eventKey, "lead", "lead-1", "LEAD_ASSIGNED", "1-0",
                "consumer-1", ConsumeStatus.PROCESSING, "owner-1", 1, 1,
                NOW.plusSeconds(60), 0, null, "{}", null, NOW, NOW);
    }

    private AckBatcher ackBatcher() {
        return new AckBatcher(new NoopAcknowledger(), 10, 100, Duration.ofMillis(10));
    }

    private TransactionOperations transactions() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
    }

    private static final class RecordingLeaseRegistrar implements LeaseRegistrar {

        private int registered;
        private int completed;

        @Override
        public void register(LeaseTask task) {
            registered++;
        }

        @Override
        public void complete(LeaseTask task) {
            completed++;
        }
    }

    private static final class NoopAcknowledger implements StreamAcknowledger {

        @Override
        public long acknowledge(String stream, String group, String... entryIds) {
            return entryIds.length;
        }
    }
}
