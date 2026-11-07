package com.wuba.crm.tx.consumer.pull;

import com.wuba.crm.tx.consumer.domain.ConsumedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StreamPuller implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(StreamPuller.class);
    private static final Duration DEFAULT_ERROR_BACKOFF = Duration.ofMillis(500);
    private static final long CLOSE_JOIN_MILLIS = 5_000L;

    private final StreamSource source;
    private final StreamQueue queue;
    private final String stream;
    private final String group;
    private final String consumer;
    private final int count;
    private final Duration block;
    private final Duration errorBackoff;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Thread thread;

    public StreamPuller(
            StreamSource source,
            StreamQueue queue,
            String stream,
            String group,
            String consumer,
            int count,
            Duration block) {
        this(source, queue, stream, group, consumer, count, block, DEFAULT_ERROR_BACKOFF);
    }

    StreamPuller(
            StreamSource source,
            StreamQueue queue,
            String stream,
            String group,
            String consumer,
            int count,
            Duration block,
            Duration errorBackoff) {
        this.source = Objects.requireNonNull(source, "source");
        this.queue = Objects.requireNonNull(queue, "queue");
        this.stream = requireText(stream, "stream");
        this.group = requireText(group, "group");
        this.consumer = requireText(consumer, "consumer");
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }
        this.count = count;
        this.block = Objects.requireNonNull(block, "block");
        this.errorBackoff = Objects.requireNonNull(errorBackoff, "errorBackoff");
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(this::runLoop, "tx-consumer-puller-" + stream);
        worker.setDaemon(true);
        this.thread = worker;
        worker.start();
        log.info("stream puller started, stream={}, group={}, consumer={}, count={}, block={}",
                stream, group, consumer, count, block);
    }

    public int pullOnce() throws InterruptedException {
        List<ConsumedMessage> messages = source.read(stream, group, consumer, count, block);
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        queue.putAll(messages);
        return messages.size();
    }

    public boolean isRunning() {
        return running.get();
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
        log.info("stream puller stopped, stream={}, group={}, consumer={}",
                stream, group, consumer);
    }

    private void runLoop() {
        while (running.get()) {
            try {
                pullOnce();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ex) {
                if (!running.get()) {
                    return;
                }
                log.warn("stream pull failed, stream={}, group={}, consumer={}",
                        stream, group, consumer, ex);
                if (!sleepBackoff()) {
                    return;
                }
            }
        }
    }

    private boolean sleepBackoff() {
        try {
            Thread.sleep(errorBackoff.toMillis());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
