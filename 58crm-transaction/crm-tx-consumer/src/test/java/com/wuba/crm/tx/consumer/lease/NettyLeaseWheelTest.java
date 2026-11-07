package com.wuba.crm.tx.consumer.lease;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyLeaseWheelTest {

    @Test
    void schedule_firesAfterDelay() throws Exception {
        NettyLeaseWheel wheel = new NettyLeaseWheel(Duration.ofMillis(10), 64);
        LeaseTask task = task("m-1");
        CountDownLatch fired = new CountDownLatch(1);
        try {
            wheel.schedule(task, Duration.ofMillis(30), ignored -> fired.countDown());
            assertTrue(fired.await(2, TimeUnit.SECONDS));
        } finally {
            wheel.close();
        }
    }

    @Test
    void cancel_preventsCallback() throws Exception {
        NettyLeaseWheel wheel = new NettyLeaseWheel(Duration.ofMillis(10), 64);
        LeaseTask task = task("m-1");
        AtomicInteger calls = new AtomicInteger();
        try {
            wheel.schedule(task, Duration.ofMillis(100), ignored -> calls.incrementAndGet());
            wheel.cancel(task);
            Thread.sleep(200);
            assertTrue(calls.get() == 0);
        } finally {
            wheel.close();
        }
    }

    private LeaseTask task(String msgId) {
        return new LeaseTask(msgId, "lead", "c-1", "owner-1", 1,
                Instant.parse("2026-01-01T01:00:00Z"));
    }
}
