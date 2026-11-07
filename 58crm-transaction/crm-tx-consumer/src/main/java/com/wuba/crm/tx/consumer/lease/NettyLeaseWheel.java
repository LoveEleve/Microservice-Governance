package com.wuba.crm.tx.consumer.lease;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class NettyLeaseWheel implements LeaseWheel {

    private static final long DEFAULT_TICK_MILLIS = 1_000L;
    private static final int DEFAULT_WHEEL_SIZE = 512;
    private static final long DEFAULT_MAX_PENDING = 1_000_000L;

    private final HashedWheelTimer timer;
    private final ConcurrentMap<String, Scheduled> timeouts = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();
    private final Object lock = new Object();

    public NettyLeaseWheel() {
        this(Duration.ofMillis(DEFAULT_TICK_MILLIS), DEFAULT_WHEEL_SIZE);
    }

    NettyLeaseWheel(Duration tick, int wheelSize) {
        if (tick.toMillis() < 1) {
            throw new IllegalArgumentException("tick must be at least 1ms");
        }
        if (wheelSize < 1) {
            throw new IllegalArgumentException("wheelSize must be positive");
        }
        AtomicInteger counter = new AtomicInteger();
        this.timer = new HashedWheelTimer(
                runnable -> {
                    Thread thread = new Thread(runnable, "tx-lease-wheel-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                tick.toMillis(),
                TimeUnit.MILLISECONDS,
                wheelSize,
                false,
                DEFAULT_MAX_PENDING);
    }

    @Override
    public void schedule(LeaseTask task, Duration delay, Consumer<LeaseTask> onDue) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(onDue, "onDue");
        synchronized (lock) {
            long currentGeneration = generation.incrementAndGet();
            TimerTask timerTask = timeout -> {
                synchronized (lock) {
                    Scheduled scheduled = timeouts.get(task.key());
                    if (scheduled == null || scheduled.generation() != currentGeneration) {
                        return;
                    }
                    timeouts.remove(task.key(), scheduled);
                }
                onDue.accept(task);
            };
            Timeout timeout = timer.newTimeout(
                    timerTask, delay.toMillis(), TimeUnit.MILLISECONDS);
            Scheduled scheduled = new Scheduled(timeout, currentGeneration);
            Scheduled previous = timeouts.put(task.key(), scheduled);
            if (previous != null) {
                previous.timeout().cancel();
            }
        }
    }

    @Override
    public void cancel(LeaseTask task) {
        if (task == null) {
            return;
        }
        synchronized (lock) {
            Scheduled scheduled = timeouts.remove(task.key());
            if (scheduled != null) {
                scheduled.timeout().cancel();
            }
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            for (Scheduled scheduled : timeouts.values()) {
                scheduled.timeout().cancel();
            }
            timeouts.clear();
            timer.stop();
        }
    }

    private record Scheduled(Timeout timeout, long generation) {
    }
}
