package com.wuba.crm.tx.consumer.lease;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class LeaseManager implements AutoCloseable, LeaseRegistrar {

    private static final Logger log = LoggerFactory.getLogger(LeaseManager.class);
    private static final int RENEWAL_QUEUE_CAPACITY = 100_000;
    private static final int RENEWAL_BATCH_SIZE = 200;
    private static final long CLOSE_JOIN_MILLIS = 5_000L;

    private final LeaseWheel wheel;
    private final InFlightRegistry registry;
    private final LeaseRenewer renewer;
    private final Duration renewAfter;
    private final Consumer<LeaseTask> onLeaseLost;
    private final Clock clock;
    private final BlockingQueue<LeaseTask> renewalQueue =
            new ArrayBlockingQueue<>(RENEWAL_QUEUE_CAPACITY);
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Thread thread;

    public LeaseManager(
            LeaseWheel wheel,
            InFlightRegistry registry,
            LeaseRenewer renewer,
            Duration renewAfter,
            Consumer<LeaseTask> onLeaseLost) {
        this(wheel, registry, renewer, renewAfter, onLeaseLost, Clock.systemUTC());
    }

    LeaseManager(
            LeaseWheel wheel,
            InFlightRegistry registry,
            LeaseRenewer renewer,
            Duration renewAfter,
            Consumer<LeaseTask> onLeaseLost,
            Clock clock) {
        this.wheel = Objects.requireNonNull(wheel, "wheel");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.renewer = Objects.requireNonNull(renewer, "renewer");
        this.renewAfter = Objects.requireNonNull(renewAfter, "renewAfter");
        this.onLeaseLost = onLeaseLost == null ? task -> {
        } : onLeaseLost;
        this.clock = Objects.requireNonNull(clock, "clock");
        if (renewAfter.isZero() || renewAfter.isNegative()) {
            throw new IllegalArgumentException("renewAfter must be positive");
        }
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(this::renewLoop, "tx-consumer-lease-renewer");
        worker.setDaemon(true);
        this.thread = worker;
        worker.start();
    }

    @Override
    public void register(LeaseTask task) {
        registry.register(task);
        try {
            wheel.schedule(task, renewAfter, this::onDue);
        } catch (RuntimeException ex) {
            registry.unregister(task);
            throw ex;
        }
    }

    @Override
    public void complete(LeaseTask task) {
        registry.unregister(task);
        wheel.cancel(task);
    }

    public int inFlightCount() {
        return registry.size();
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
        wheel.close();
    }

    private void onDue(LeaseTask task) {
        if (!registry.contains(task)) {
            return;
        }
        if (!renewalQueue.offer(task)) {
            log.warn("lease renewal queue full, task={}", task.key());
        }
    }

    private void renewLoop() {
        while (running.get()) {
            List<LeaseTask> batch = new ArrayList<>(RENEWAL_BATCH_SIZE);
            try {
                batch.add(renewalQueue.take());
                renewalQueue.drainTo(batch, RENEWAL_BATCH_SIZE - 1);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            List<LeaseTask> renewable = new ArrayList<>(batch.size());
            for (LeaseTask task : batch) {
                if (!registry.contains(task)) {
                    continue;
                }
                if (task.isExpired(clock.instant())) {
                    leaseLost(task);
                } else {
                    renewable.add(task);
                }
            }
            if (renewable.isEmpty()) {
                continue;
            }
            try {
                Set<String> renewed = renewer.renewBatch(renewable);
                for (LeaseTask task : renewable) {
                    if (renewed.contains(task.key())) {
                        wheel.schedule(task, renewAfter, this::onDue);
                    } else {
                        leaseLost(task);
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("lease renewal batch failed, size={}", renewable.size(), ex);
                for (LeaseTask task : renewable) {
                    leaseLost(task);
                }
            }
        }
    }

    private void leaseLost(LeaseTask task) {
        registry.unregister(task);
        wheel.cancel(task);
        try {
            onLeaseLost.accept(task);
        } catch (RuntimeException ex) {
            log.warn("lease lost callback failed, task={}", task.key(), ex);
        }
    }
}
