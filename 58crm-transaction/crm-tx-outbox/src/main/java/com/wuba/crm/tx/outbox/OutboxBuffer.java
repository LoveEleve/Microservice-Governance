package com.wuba.crm.tx.outbox;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 有界 Outbox 内存缓冲。
 *
 * <p>它对应 Kafka 的 {@code RecordAccumulator}：扫描线程只负责放入，发送线程
 * 按窗口取走。容量满时 {@link #put(OutboxRecord)} 阻塞，形成背压，避免 8W
 * 峰值把 JVM 堆打爆。</p>
 */
public class OutboxBuffer {

    private final ArrayBlockingQueue<OutboxRecord> queue;

    public OutboxBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    public void put(OutboxRecord record) throws InterruptedException {
        queue.put(record);
    }

    public OutboxRecord poll(Duration timeout) throws InterruptedException {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return queue.poll();
        }
        return queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public int drainTo(List<OutboxRecord> target, int maxElements) {
        return queue.drainTo(target, maxElements);
    }

    public int size() {
        return queue.size();
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }
}
