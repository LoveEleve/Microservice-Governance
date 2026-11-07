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
import java.util.function.Consumer;

public final class NettyLeaseWheel implements LeaseWheel {

    private static final long DEFAULT_TICK_MILLIS = 1_000L;
    private static final int DEFAULT_WHEEL_SIZE = 512;
    private static final long DEFAULT_MAX_PENDING = 1_000_000L;

    private final HashedWheelTimer timer;
    private final ConcurrentMap<String, Timeout> timeouts = new ConcurrentHashMap<>();

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
        TimerTask timerTask = timeout -> {
            timeouts.remove(task.key());
            onDue.accept(task);
        };
        Timeout timeout = timer.newTimeout(timerTask, delay.toMillis(), TimeUnit.MILLISECONDS);
        Timeout previous = timeouts.put(task.key(), timeout);
        if (previous != null) {
            previous.cancel();
        }
    }

    @Override
    public void cancel(LeaseTask task) {
        if (task == null) {
            return;
        }
        Timeout timeout = timeouts.remove(task.key());
        if (timeout != null) {
            timeout.cancel();
        }
    }

    @Override
    public void close() {
        for (Timeout timeout : timeouts.values()) {
            timeout.cancel();
        }
        timeouts.clear();
        timer.stop();
    }
}
