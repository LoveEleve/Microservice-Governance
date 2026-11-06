package com.wuba.crm.tx.outbox.producer;

import com.wuba.crm.tx.outbox.pipeline.interceptor.OutboxInterceptorChain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Outbox 发送端生命周期：扫描线程 + 独立发送线程。
 *
 * <p>扫描和发送彻底解耦：扫描只读 MySQL、写 buffer；发送只读 buffer、写 Redis Stream、
 * 回写 MySQL 状态。任一侧变慢通过有界 buffer 形成背压。</p>
 */
public final class OutboxProducer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OutboxProducer.class);

    private final OutboxScanner scanner;
    private final List<OutboxSender> senders;
    private final OutboxInterceptorChain interceptors;
    private final OutboxConfig config;
    // 防止 start() 被调用两次，起出两套扫描/发送线程。
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();

    private ScheduledExecutorService scannerExecutor;
    private ExecutorService senderExecutor;

    public OutboxProducer(
            OutboxScanner scanner,
            OutboxSender sender,
            OutboxInterceptorChain interceptors,
            OutboxConfig config) {
        this(scanner, List.of(sender), interceptors, config);
    }

    public OutboxProducer(
            OutboxScanner scanner,
            List<OutboxSender> senders,
            OutboxInterceptorChain interceptors,
            OutboxConfig config) {
        if (senders == null || senders.isEmpty()) {
            throw new IllegalArgumentException("senders must not be empty");
        }
        this.scanner = scanner;
        this.senders = List.copyOf(senders);
        this.interceptors = interceptors;
        this.config = config;
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("outbox producer already started");
        }
        running.set(true);

        scannerExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tx-outbox-scanner");
            thread.setDaemon(true);
            return thread;
        });
        AtomicInteger senderIndex = new AtomicInteger();
        senderExecutor = Executors.newFixedThreadPool(senders.size(), runnable -> {
            Thread thread = new Thread(
                    runnable, "tx-outbox-sender-" + senderIndex.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });

        // Scanner 和 Sender 分开线程：扫描慢不会阻塞发送，发送慢会通过 buffer 反压扫描。
        scannerExecutor.scheduleWithFixedDelay(
                this::scanSafely,
                0,
                config.scanInterval().toMillis(),
                TimeUnit.MILLISECONDS);
        for (OutboxSender sender : senders) {
            senderExecutor.submit(() -> sendLoop(sender));
        }
    }

    private void scanSafely() {
        if (!running.get()) {
            return;
        }
        try {
            scanner.scanOnce();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ex) {
            log.warn("outbox scan failed", ex);
        }
    }

    private void sendLoop(OutboxSender sender) {
        // 每个 Sender 只 drain 自己 assignedBizCodes 的队列。
        while (running.get()) {
            try {
                sender.sendOnce();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ex) {
                // 单轮失败不能杀死发送线程；下一轮继续处理。
                log.warn("outbox send failed", ex);
            }
        }
    }

    /**
     * 当前实际启动的 Sender shard 数，用于指标和测试。
     */
    public int senderCount() {
        return senders.size();
    }

    @Override
    public void close() {
        running.set(false);
        if (scannerExecutor != null) {
            scannerExecutor.shutdownNow();
        }
        if (senderExecutor != null) {
            senderExecutor.shutdownNow();
            try {
                senderExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        interceptors.close();
    }
}
