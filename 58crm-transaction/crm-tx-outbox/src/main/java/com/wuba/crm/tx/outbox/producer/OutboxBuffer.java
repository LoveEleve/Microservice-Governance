package com.wuba.crm.tx.outbox.producer;

import com.wuba.crm.tx.outbox.domain.OutboxRecord;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;
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
    private final int perBizMaxMessages;
    private final long perBizMaxBytes;
    private final Map<String, Long> bizBytes = new HashMap<>();
    private int messageCount;
    private long byteCount;

    public OutboxBuffer(int maxMessages, long maxBytes) {
        this(maxMessages, maxBytes, maxMessages, maxBytes);
    }

    public OutboxBuffer(
            int maxMessages, long maxBytes,
            int perBizMaxMessages, long perBizMaxBytes) {
        if (maxMessages <= 0 || maxBytes <= 0
                || perBizMaxMessages <= 0 || perBizMaxBytes <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.maxMessages = maxMessages;
        this.maxBytes = maxBytes;
        this.perBizMaxMessages = perBizMaxMessages;
        this.perBizMaxBytes = perBizMaxBytes;
    }

    /**
     * 放入一条待发送消息。容量不足时阻塞，这是生产端背压的入口。
     */
    public void put(OutboxRecord record) throws InterruptedException {
        long bytes = estimateBytes(record);
        // 单条就超过总容量时，等待永远不会结束；必须拒绝并让上层转 DEAD。
        if (bytes > maxBytes || bytes > perBizMaxBytes) {
            throw new MessageTooLargeException(record.msgId(), bytes, maxBytes);
        }
        lock.lockInterruptibly();
        try {
            // 这里会让出 CPU 等待；Scanner 使用 tryPut 避免被单个业务线卡住。
            while (!hasCapacityLocked(record.bizCode(), bytes)) {
                notFull.await();
            }
            addLocked(record, bytes);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 非阻塞入队。某个业务线达到 per-biz 上限时返回 false，
     * Scanner 可以跳过它继续处理其他业务线。
     */
    public boolean tryPut(OutboxRecord record) throws InterruptedException {
        long bytes = estimateBytes(record);
        // 超容量不是“队列满”，必须抛异常，让 Scanner 转 DEAD；
        // 返回 false 只表示暂时没有容量。
        if (bytes > maxBytes || bytes > perBizMaxBytes) {
            throw new MessageTooLargeException(record.msgId(), bytes, maxBytes);
        }
        lock.lockInterruptibly();
        try {
            if (!hasCapacityLocked(record.bizCode(), bytes)) {
                return false;
            }
            addLocked(record, bytes);
            return true;
        } finally {
            lock.unlock();
        }
    }

    private boolean hasCapacityLocked(String bizCode, long bytes) {
        if (messageCount >= maxMessages || byteCount + bytes > maxBytes) {
            return false;
        }
        ArrayDeque<OutboxRecord> queue = queues.get(bizCode);
        if (queue != null && queue.size() >= perBizMaxMessages) {
            return false;
        }
        long currentBizBytes = bizBytes.getOrDefault(bizCode, 0L);
        return currentBizBytes + bytes <= perBizMaxBytes;
    }

    private void addLocked(OutboxRecord record, long bytes) {
        ArrayDeque<OutboxRecord> queue = queues.computeIfAbsent(
                record.bizCode(), ignored -> new ArrayDeque<>());
        if (queue.isEmpty()) {
            ready.addLast(record.bizCode());
        }
        queue.addLast(record);
        bizBytes.merge(record.bizCode(), bytes, Long::sum);
        messageCount++;
        byteCount += bytes;
        notEmpty.signalAll();
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
     * 从指定业务线集合中取一条；供一个 Sender shard 只处理自己负责的队列。
     */
    public OutboxRecord poll(Set<String> bizCodes, Duration timeout) throws InterruptedException {
        long deadline = deadlineNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (true) {
                // 使用快照遍历，避免在迭代 ready 时修改它。
                // 取完一个 bizCode 后，如果它还有消息，把它移到队尾，形成公平轮转。
                java.util.List<String> snapshot = new java.util.ArrayList<>(ready);
                for (String bizCode : snapshot) {
                    if (!bizCodes.contains(bizCode)) {
                        continue;
                    }
                    ArrayDeque<OutboxRecord> queue = queues.get(bizCode);
                    if (queue == null || queue.isEmpty()) {
                        ready.remove(bizCode);
                        continue;
                    }
                    OutboxRecord record = queue.removeFirst();
                    ready.remove(bizCode);
                    if (!queue.isEmpty()) {
                        // 还有消息：排到队尾，下一次先处理其它活跃业务线。
                        ready.addLast(bizCode);
                    }
                    long bytes = estimateBytes(record);
                    messageCount--;
                    byteCount -= bytes;
                    bizBytes.computeIfPresent(bizCode, (key, value) -> value - bytes <= 0 ? null : value - bytes);
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
                    long bytes = estimateBytes(record);
                    messageCount--;
                    byteCount -= bytes;
                    bizBytes.computeIfPresent(bizCode, (key, value) -> value - bytes <= 0 ? null : value - bytes);
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
                long bytes = estimateBytes(record);
                messageCount--;
                byteCount -= bytes;
                bizBytes.computeIfPresent(bizCode, (key, value) -> value - bytes <= 0 ? null : value - bytes);
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
        long bytes = estimateBytes(record);
        messageCount--;
        byteCount -= bytes;
        bizBytes.computeIfPresent(bizCode, (key, value) -> value - bytes <= 0 ? null : value - bytes);
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
