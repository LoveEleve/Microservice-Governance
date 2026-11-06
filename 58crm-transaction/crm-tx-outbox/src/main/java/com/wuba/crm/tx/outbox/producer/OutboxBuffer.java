package com.wuba.crm.tx.outbox.producer;

import com.wuba.crm.tx.outbox.domain.OutboxRecord;

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

    // 每个业务线一个队列，等价于 Kafka 的 per-partition Deque。
    private final Map<String, ArrayDeque<OutboxRecord>> queues = new HashMap<>();
    // 记录哪些业务线当前有消息，发送线程轮询这个队列，避免每次都遍历整个 Map。
    private final Deque<String> ready = new ArrayDeque<>();
    // 所有 count/byte/ready/queues 的修改都必须持有这把锁。
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();

    // 双重容量限制：条数防小消息把队列撑爆，字节数防大 payload 把 JVM 内存撑爆。
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

    /**
     * 放入一条待发送消息。容量不足时阻塞，这是生产端背压的入口。
     */
    public void put(OutboxRecord record) throws InterruptedException {
        long bytes = estimateBytes(record);
        lock.lockInterruptibly();
        try {
            // 先等容量。这里不能用 offer：队列满时必须让 Scanner 慢下来，
            // 不能把消息丢掉，也不能把内存撑爆。
            while (messageCount >= maxMessages || byteCount + bytes > maxBytes) {
                notFull.await();
            }
            ArrayDeque<OutboxRecord> queue = queues.computeIfAbsent(
                    record.bizCode(), ignored -> new ArrayDeque<>());
            // 只有从空变非空时才加入 ready，避免同一个 bizCode 重复排队。
            if (queue.isEmpty()) {
                ready.addLast(record.bizCode());
            }
            queue.addLast(record);
            messageCount++;
            byteCount += bytes;
            // 唤醒可能在等第一条消息的 Sender。
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 从任意业务线取一条消息；没有数据时最多等待 timeout。
     */
    public OutboxRecord poll(Duration timeout) throws InterruptedException {
        long deadline = deadlineNanos(timeout);
        lock.lockInterruptibly();
        try {
            // 没有 ready 业务线时等待 Scanner 唤醒；超时返回 null。
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

    /**
     * 只从指定业务线取一条消息，用于 Sender 在 linger 窗口内继续聚合同一业务线。
     */
    public OutboxRecord poll(String bizCode, Duration timeout) throws InterruptedException {
        long deadline = deadlineNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (true) {
                ArrayDeque<OutboxRecord> queue = queues.get(bizCode);
                if (queue != null && !queue.isEmpty()) {
                    OutboxRecord record = queue.removeFirst();
                    // 业务线队列被取空时，从 ready 中移除，避免留下空引用。
                    if (queue.isEmpty()) {
                        ready.remove(bizCode);
                    }
                    messageCount--;
                    byteCount -= estimateBytes(record);
                    // 释放容量，唤醒可能被 put 阻塞的 Scanner。
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

    /**
     * 一次取走指定业务线最多 maxElements 条，返回实际取走数量。
     */
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
            // 队列清空后必须从 ready 里移除，否则 poll() 会拿到空队列。
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
        // poll(Duration) 已经确认 ready 非空；这里按 ready 顺序取，形成业务线间的简单轮转。
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

    // 估算消息在堆内的大致占用；不追求精确，只用于触发背压保护。
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
