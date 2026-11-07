package com.wuba.crm.tx.consumer.pull;

import com.wuba.crm.tx.consumer.domain.ConsumedMessage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class BoundedStreamQueue implements StreamQueue {

    private final BlockingQueue<ConsumedMessage> queue;

    public BoundedStreamQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    @Override
    public void putAll(List<ConsumedMessage> messages) throws InterruptedException {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (ConsumedMessage message : messages) {
            queue.put(Objects.requireNonNull(message, "message"));
        }
    }

    @Override
    public List<ConsumedMessage> takeBatch(int max, Duration timeout) throws InterruptedException {
        if (max < 1) {
            throw new IllegalArgumentException("max must be positive");
        }
        Objects.requireNonNull(timeout, "timeout");
        List<ConsumedMessage> batch = new ArrayList<>(max);
        ConsumedMessage first = queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (first == null) {
            return batch;
        }
        batch.add(first);
        queue.drainTo(batch, max - 1);
        return batch;
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public void clear() {
        queue.clear();
    }
}
