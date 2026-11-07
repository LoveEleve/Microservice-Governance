package com.wuba.crm.tx.consumer.pull;

import com.wuba.crm.tx.consumer.domain.ConsumedMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamPullerTest {

    @Test
    void pullOnce_readsBatchAndEnqueues() throws Exception {
        BoundedStreamQueue queue = new BoundedStreamQueue(10);
        StreamPuller puller = new StreamPuller(
                (stream, group, consumer, count, block) -> List.of(message("m-1")),
                queue, "tx:stream:lead", "tx-group-lead", "consumer-1",
                10, Duration.ofMillis(10));

        assertEquals(1, puller.pullOnce());
        assertEquals(1, queue.size());
    }

    @Test
    void pullOnce_emptyReadReturnsZero() throws Exception {
        BoundedStreamQueue queue = new BoundedStreamQueue(10);
        StreamPuller puller = new StreamPuller(
                (stream, group, consumer, count, block) -> List.of(),
                queue, "tx:stream:lead", "tx-group-lead", "consumer-1",
                10, Duration.ofMillis(10));

        assertEquals(0, puller.pullOnce());
        assertTrue(queue.size() == 0);
    }

    @Test
    void close_stopsBlockingRead() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        StreamSource blockingSource = (stream, group, consumer, count, block) -> {
            started.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted");
            }
            return List.of();
        };
        BoundedStreamQueue queue = new BoundedStreamQueue(10);
        StreamPuller puller = new StreamPuller(
                blockingSource, queue, "tx:stream:lead", "tx-group-lead", "consumer-1",
                10, Duration.ofMillis(10));

        puller.start();
        assertTrue(started.await(1, TimeUnit.SECONDS));
        puller.close();

        assertFalse(puller.isRunning());
    }

    @Test
    void start_isIdempotent() {
        BoundedStreamQueue queue = new BoundedStreamQueue(10);
        AtomicInteger calls = new AtomicInteger();
        StreamPuller puller = new StreamPuller(
                (stream, group, consumer, count, block) -> {
                    calls.incrementAndGet();
                    return List.of();
                },
                queue, "tx:stream:lead", "tx-group-lead", "consumer-1",
                10, Duration.ofMillis(10));

        puller.start();
        puller.start();
        puller.close();

        assertFalse(puller.isRunning());
    }

    private ConsumedMessage message(String msgId) {
        return new ConsumedMessage(
                msgId, "lead", "lead-1", "LEAD_ASSIGNED", "1-0", "{}", null);
    }
}
