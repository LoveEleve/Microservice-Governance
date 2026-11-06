package com.wuba.crm.tx.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Outbox 投递窗口。
 *
 * <p>业务事务只负责写 MySQL；这个调度器每 10ms 触发一次 Relay，把窗口内可投递的
 * 消息按业务线合并成批。单批上限默认 256，避免一次 pipeline 过大；峰值时一个窗口
 * 可以连续跑多批。</p>
 */
public final class OutboxRelayScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxRelay relay;
    private final Duration window;
    private final int batchSize;
    private final int maxBatchesPerWindow;
    private final AtomicBoolean started = new AtomicBoolean();

    private ScheduledExecutorService executor;

    public OutboxRelayScheduler(
            OutboxRelay relay,
            Duration window,
            int batchSize,
            int maxBatchesPerWindow) {
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (maxBatchesPerWindow <= 0) {
            throw new IllegalArgumentException("maxBatchesPerWindow must be positive");
        }
        this.relay = relay;
        this.window = window;
        this.batchSize = batchSize;
        this.maxBatchesPerWindow = maxBatchesPerWindow;
    }

    /**
     * 启动周期投递。重复调用会抛异常，避免同一实例起多个调度线程。
     */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("relay scheduler already started");
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tx-outbox-relay");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(
                this::flushSafely,
                0,
                window.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * 执行一个窗口的投递，返回本轮实际投递数。
     */
    public int flush() {
        int relayed = 0;
        for (int i = 0; i < maxBatchesPerWindow; i++) {
            int current = relay.relayOnce(batchSize);
            if (current == 0) {
                break;
            }
            relayed += current;
        }
        return relayed;
    }

    private void flushSafely() {
        try {
            flush();
        } catch (RuntimeException ex) {
            log.warn("outbox relay window failed", ex);
        }
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
