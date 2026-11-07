package com.wuba.crm.tx.consumer.ack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AckBatcher implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AckBatcher.class);
    private static final long CLOSE_JOIN_MILLIS = 5_000L;

    private final BlockingQueue<AckCommand> queue;
    private final StreamAcknowledger acknowledger;
    private final int batchSize;
    private final Duration flushInterval;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Thread thread;

    public AckBatcher(
            StreamAcknowledger acknowledger,
            int batchSize,
            int queueCapacity,
            Duration flushInterval) {
        this.acknowledger = Objects.requireNonNull(acknowledger, "acknowledger");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        this.batchSize = batchSize;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.flushInterval = Objects.requireNonNull(flushInterval, "flushInterval");
    }

    public boolean add(AckCommand command) {
        boolean accepted = queue.offer(Objects.requireNonNull(command, "command"));
        if (!accepted) {
            log.warn("ack queue full, drop ack command, stream={}, entryId={}",
                    command.stream(), command.entryId());
        }
        return accepted;
    }

    public int flushOnce() throws InterruptedException {
        List<AckCommand> batch = new ArrayList<>(batchSize);
        AckCommand first = queue.poll(flushInterval.toMillis(), TimeUnit.MILLISECONDS);
        if (first == null) {
            return 0;
        }
        batch.add(first);
        queue.drainTo(batch, batchSize - 1);

        Map<AckGroup, List<String>> groups = new LinkedHashMap<>();
        for (AckCommand command : batch) {
            groups.computeIfAbsent(
                            new AckGroup(command.stream(), command.group()),
                            ignored -> new ArrayList<>())
                    .add(command.entryId());
        }
        for (Map.Entry<AckGroup, List<String>> entry : groups.entrySet()) {
            List<String> entryIds = entry.getValue();
            acknowledger.acknowledge(
                    entry.getKey().stream(),
                    entry.getKey().group(),
                    entryIds.toArray(String[]::new));
        }
        return batch.size();
    }

    public int size() {
        return queue.size();
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(this::runLoop, "tx-consumer-ack-batcher");
        worker.setDaemon(true);
        this.thread = worker;
        worker.start();
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

    private void runLoop() {
        while (running.get()) {
            try {
                flushOnce();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ex) {
                if (!running.get()) {
                    return;
                }
                log.warn("ack batch failed", ex);
            }
        }
    }

    private record AckGroup(String stream, String group) {
    }
}
