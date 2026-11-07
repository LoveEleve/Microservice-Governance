package com.wuba.crm.tx.consumer.persist;

import com.wuba.crm.tx.consumer.domain.ConsumedMessage;
import com.wuba.crm.tx.consumer.persistence.ConsumerRecordRepository;
import com.wuba.crm.tx.consumer.pull.StreamQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConsumePersister implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ConsumePersister.class);
    private static final long CLOSE_JOIN_MILLIS = 5_000L;

    private final StreamQueue queue;
    private final ConsumerRecordRepository repository;
    private final int batchSize;
    private final Duration pollTimeout;
    private final Duration errorBackoff;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Thread thread;

    public ConsumePersister(
            StreamQueue queue,
            ConsumerRecordRepository repository,
            int batchSize,
            Duration pollTimeout) {
        this(queue, repository, batchSize, pollTimeout, Duration.ofMillis(500));
    }

    ConsumePersister(
            StreamQueue queue,
            ConsumerRecordRepository repository,
            int batchSize,
            Duration pollTimeout,
            Duration errorBackoff) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.repository = Objects.requireNonNull(repository, "repository");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
        this.pollTimeout = Objects.requireNonNull(pollTimeout, "pollTimeout");
        this.errorBackoff = Objects.requireNonNull(errorBackoff, "errorBackoff");
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(this::runLoop, "tx-consumer-persister");
        worker.setDaemon(true);
        this.thread = worker;
        worker.start();
        log.info("consumer persister started, batchSize={}, pollTimeout={}",
                batchSize, pollTimeout);
    }

    public int persistOnce() throws InterruptedException {
        List<ConsumedMessage> batch = queue.takeBatch(batchSize, pollTimeout);
        if (batch.isEmpty()) {
            return 0;
        }
        int persisted = repository.insertReadyBatch(batch, Instant.now());
        log.debug("consumer batch persisted, batchSize={}, inserted={}", batch.size(), persisted);
        return persisted;
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
        log.info("consumer persister stopped");
    }

    private void runLoop() {
        while (running.get()) {
            try {
                persistOnce();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ex) {
                if (!running.get()) {
                    return;
                }
                log.warn("persist consumer messages failed", ex);
                if (!sleepBackoff()) {
                    return;
                }
            }
        }
    }

    private boolean sleepBackoff() {
        try {
            Thread.sleep(errorBackoff.toMillis());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
