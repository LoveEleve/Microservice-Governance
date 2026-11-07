package com.wuba.crm.tx.consumer.worker;

import com.wuba.crm.tx.consumer.ack.AckBatcher;
import com.wuba.crm.tx.consumer.ack.AckCommand;
import com.wuba.crm.tx.consumer.domain.ClaimRequest;
import com.wuba.crm.tx.consumer.domain.ConsumedMessage;
import com.wuba.crm.tx.consumer.domain.ConsumerRecord;
import com.wuba.crm.tx.consumer.lease.LeaseRegistrar;
import com.wuba.crm.tx.consumer.lease.LeaseTask;
import com.wuba.crm.tx.consumer.persistence.ConsumerRecordRepository;
import com.wuba.crm.tx.consumer.persistence.IdempotentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConsumerWorker implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ConsumerWorker.class);
    private static final long CLOSE_JOIN_MILLIS = 5_000L;
    private static final long ERROR_BACKOFF_MILLIS = 500L;
    private static final int CLAIM_DEADLOCK_RETRIES = 3;

    private final ConsumerWorkerConfig config;
    private final ConsumerRecordRepository repository;
    private final IdempotentRepository idempotent;
    private final HandlerRegistry handlers;
    private final TransactionOperations transactions;
    private final LeaseRegistrar leases;
    private final AckBatcher ackBatcher;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Thread thread;

    public ConsumerWorker(
            ConsumerWorkerConfig config,
            ConsumerRecordRepository repository,
            IdempotentRepository idempotent,
            HandlerRegistry handlers,
            TransactionOperations transactions,
            LeaseRegistrar leases,
            AckBatcher ackBatcher) {
        this(config, repository, idempotent, handlers, transactions, leases, ackBatcher,
                Clock.systemUTC());
    }

    ConsumerWorker(
            ConsumerWorkerConfig config,
            ConsumerRecordRepository repository,
            IdempotentRepository idempotent,
            HandlerRegistry handlers,
            TransactionOperations transactions,
            LeaseRegistrar leases,
            AckBatcher ackBatcher,
            Clock clock) {
        this.config = Objects.requireNonNull(config, "config");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.idempotent = Objects.requireNonNull(idempotent, "idempotent");
        this.handlers = Objects.requireNonNull(handlers, "handlers");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.leases = Objects.requireNonNull(leases, "leases");
        this.ackBatcher = Objects.requireNonNull(ackBatcher, "ackBatcher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(this::runLoop,
                "tx-consumer-worker-" + config.bizCode() + "-" + config.consumerId());
        worker.setDaemon(true);
        this.thread = worker;
        worker.start();
        log.info("consumer worker started, bizCode={}, consumer={}, stream={}, group={}, batchSize={}",
                config.bizCode(), config.consumerId(), config.stream(), config.group(),
                config.batchSize());
    }

    public int pollOnce() {
        Instant now = clock.instant();
        ClaimRequest request = new ClaimRequest(
                config.bizCode(),
                config.consumerId(),
                UUID.randomUUID().toString(),
                config.batchSize(),
                now,
                now.plus(config.leaseDuration()));
        List<ConsumerRecord> records = claimWithRetry(request);
        log.debug("consumer worker claimed batch, bizCode={}, claimed={}",
                config.bizCode(), records.size());
        List<LeaseTask> leases = registerLeases(records);
        for (int i = 0; i < records.size(); i++) {
            process(records.get(i), leases.get(i));
        }
        return records.size();
    }

    private List<ConsumerRecord> claimWithRetry(ClaimRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                List<ConsumerRecord> records = transactions.execute(
                        status -> repository.claimReadyBatch(request));
                return records == null ? List.of() : records;
            } catch (DeadlockLoserDataAccessException ex) {
                if (attempt >= CLAIM_DEADLOCK_RETRIES) {
                    throw ex;
                }
                try {
                    Thread.sleep(2L << attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while retrying claim", interrupted);
                }
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() {
        running.set(false);
        Thread worker = thread;
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join(CLOSE_JOIN_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private List<LeaseTask> registerLeases(List<ConsumerRecord> records) {
        List<LeaseTask> registered = new ArrayList<>(records.size());
        try {
            for (ConsumerRecord record : records) {
                LeaseTask lease = new LeaseTask(
                        record.msgId(),
                        record.bizCode(),
                        config.consumerId(),
                        record.ownerToken(),
                        record.ownerEpoch(),
                        clock.instant().plus(config.maxProcessing()));
                leases.register(lease);
                registered.add(lease);
            }
            return registered;
        } catch (RuntimeException ex) {
            for (LeaseTask lease : registered) {
                leases.complete(lease);
            }
            throw ex;
        }
    }

    private void process(ConsumerRecord record, LeaseTask lease) {
        try {
            transactions.executeWithoutResult(status -> {
                boolean firstDelivery = idempotent.tryAcquire(
                        record.msgId(), record.msgId(), clock.instant());
                if (firstDelivery) {
                    MessageHandler handler = handlers.require(record.bizCode(), record.eventType());
                    try {
                        handler.handle(toMessage(record));
                    } catch (Exception ex) {
                        throw new MessageHandlingException(record.msgId(), ex);
                    }
                }
                int updated = repository.markConsumed(
                        record.msgId(),
                        record.ownerToken(),
                        record.ownerEpoch(),
                        clock.instant());
                if (updated != 1) {
                    throw new LeaseLostException(record.msgId());
                }
            });
            boolean queued = ackBatcher.add(
                    new AckCommand(config.stream(), config.group(), record.streamEntryId()));
            if (!queued) {
                log.warn("ack queue full, msgId={}, entryId={}",
                        record.msgId(), record.streamEntryId());
            }
        } catch (LeaseLostException ex) {
            log.warn("consume lease lost, msgId={}, ownerEpoch={}",
                    record.msgId(), record.ownerEpoch());
        } catch (RuntimeException ex) {
            log.warn("consume failed, msgId={}, ownerEpoch={}",
                    record.msgId(), record.ownerEpoch(), ex);
            try {
                repository.markRetry(
                        record.msgId(),
                        record.ownerToken(),
                        record.ownerEpoch(),
                        clock.instant().plus(config.retryAfter()),
                        config.maxRetries(),
                        clock.instant());
            } catch (RuntimeException retryEx) {
                log.warn("mark retry failed, msgId={}", record.msgId(), retryEx);
            }
        } finally {
            leases.complete(lease);
        }
    }

    private ConsumedMessage toMessage(ConsumerRecord record) {
        return new ConsumedMessage(
                record.msgId(),
                record.bizCode(),
                record.bizId(),
                record.eventType(),
                record.streamEntryId(),
                record.payload(),
                record.payloadRef());
    }

    private void runLoop() {
        while (running.get()) {
            try {
                if (pollOnce() == 0) {
                    Thread.sleep(config.idlePoll().toMillis());
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ex) {
                if (!running.get()) {
                    return;
                }
                log.warn("consumer worker poll failed, bizCode={}", config.bizCode(), ex);
                try {
                    Thread.sleep(ERROR_BACKOFF_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
