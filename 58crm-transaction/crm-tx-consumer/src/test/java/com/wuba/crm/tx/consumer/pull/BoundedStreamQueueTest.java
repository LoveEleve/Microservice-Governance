package com.wuba.crm.tx.consumer.pull;

import com.wuba.crm.tx.consumer.domain.ConsumedMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedStreamQueueTest {

    @Test
    void takeBatch_returnsUpToMax() throws Exception {
        BoundedStreamQueue queue = new BoundedStreamQueue(10);
        queue.putAll(List.of(message("m-1"), message("m-2"), message("m-3")));

        List<ConsumedMessage> batch = queue.takeBatch(2, Duration.ofMillis(10));

        assertEquals(2, batch.size());
        assertEquals(1, queue.size());
    }

    @Test
    void takeBatch_timeoutReturnsEmpty() throws Exception {
        BoundedStreamQueue queue = new BoundedStreamQueue(1);

        assertTrue(queue.takeBatch(10, Duration.ofMillis(10)).isEmpty());
    }

    @Test
    void putAll_blocksWhenFull() throws Exception {
        BoundedStreamQueue queue = new BoundedStreamQueue(1);
        queue.putAll(List.of(message("m-1")));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> blocked = executor.submit(() -> {
                try {
                    queue.putAll(List.of(message("m-2")));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });

            Thread.sleep(100);
            assertFalse(blocked.isDone());

            queue.takeBatch(1, Duration.ofMillis(100));
            blocked.get(1, TimeUnit.SECONDS);
            assertTrue(blocked.isDone());
        } finally {
            executor.shutdownNow();
        }
    }

    private ConsumedMessage message(String msgId) {
        return new ConsumedMessage(
                msgId, "lead", "lead-1", "LEAD_ASSIGNED", "1-0", "{}", null);
    }
}
