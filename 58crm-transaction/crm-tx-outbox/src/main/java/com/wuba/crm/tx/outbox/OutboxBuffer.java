package com.wuba.crm.tx.outbox;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 按业务线分桶的有界 Outbox 内存缓冲。
 *
 * <p>对应 Kafka 的 {@code RecordAccumulator}：每个 partition（bizCode）一个队列，
 * 总量同时受消息条数和字节数限制。buffer 满时扫描线程阻塞，形成背压。</p>
 */
public class OutboxBuffer {

    private final Map<String, ArrayDeque<OutboxRecord>> queues = new HashMap<>();
    private final Deque<String> ready = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();

    private final int maxMessages;
    private final long maxBytes;
    private int messageCount;
    private long byteCount;

    public OutboxBuffer(int maxMessages, long maxBytes) {
        if (maxMessages <= 0 || maxBytes <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.maxMessages = maxMessages;
        this.maxBytes = maxBytes;
    }

    public void put(OutboxRecord record) throws InterruptedException {
        long bytes = estimateBytes(record);
        lock.lockInterruptibly();
        try {
            while (messageCount >= maxMessages || byteCount + bytes > maxBytes) {
                notFull.await();
            }
            ArrayDeque<OutboxRecord> queue = queues.computeIfAbsent(
                    record.bizCode(), ignored -> new ArrayDeque<>());
            if (queue.isEmpty()) {
                ready.addLast(record.bizCode());
            }
            queue.addLast(record);
            messageCount++;
            byteCount += bytes;
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public OutboxRecord poll(Duration timeout) throws InterruptedException {
        long deadline = deadlineNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (ready.isEmpty()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return null;
                }
                notEmpty.awaitNanos(remaining);
            }
            return removeOneLocked();
        } finally {
            lock.unlock();
        }
    }

    public OutboxRecord poll(String bizCode, Duration timeout) throws InterruptedException {
        long deadline = deadlineNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (true) {
                ArrayDeque<OutboxRecord> queue = queues.get(bizCode);
                if (queue != null && !queue.isEmpty()) {
                    OutboxRecord record = queue.removeFirst();
                    if (queue.isEmpty()) {
                        ready.remove(bizCode);
                    }
                    messageCount--;
                    byteCount -= estimateBytes(record);
                    notFull.signalAll();
                    return record;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return null;
                }
                notEmpty.awaitNanos(remaining);
            }
        } finally {
            lock.unlock();
        }
    }

    public int drain(String bizCode, List<OutboxRecord> target, int maxElements) {
        lock.lock();
        try {
            ArrayDeque<OutboxRecord> queue = queues.get(bizCode);
            if (queue == null || queue.isEmpty() || maxElements <= 0) {
                return 0;
            }
            int drained = 0;
            while (drained < maxElements && !queue.isEmpty()) {
                OutboxRecord record = queue.removeFirst();
                messageCount--;
                byteCount -= estimateBytes(record);
                target.add(record);
                drained++;
            }
            if (queue.isEmpty()) {
                ready.remove(bizCode);
            }
            notFull.signalAll();
            return drained;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return messageCount;
        } finally {
            lock.unlock();
        }
    }

    public int remainingCapacity() {
        lock.lock();
        try {
            return maxMessages - messageCount;
        } finally {
            lock.unlock();
        }
    }

    private OutboxRecord removeOneLocked() {
        String bizCode = ready.peekFirst();
        ArrayDeque<OutboxRecord> queue = queues.get(bizCode);
        OutboxRecord record = queue.removeFirst();
        if (queue.isEmpty()) {
            ready.removeFirst();
        }
        messageCount--;
        byteCount -= estimateBytes(record);
        notFull.signalAll();
        return record;
    }

    private static long deadlineNanos(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return System.nanoTime();
        }
        return System.nanoTime() + timeout.toNanos();
    }

    private static long estimateBytes(OutboxRecord record) {
        long bytes = 256;
        if (record.payload() != null) {
            bytes += record.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }
        if (record.payloadRef() != null) {
            bytes += record.payloadRef().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }
        return bytes;
    }
}
