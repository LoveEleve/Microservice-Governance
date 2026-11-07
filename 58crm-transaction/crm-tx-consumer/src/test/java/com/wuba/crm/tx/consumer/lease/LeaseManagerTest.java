package com.wuba.crm.tx.consumer.lease;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaseManagerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void due_renewsAndReschedules() throws Exception {
        ManualLeaseWheel wheel = new ManualLeaseWheel();
        InFlightRegistry registry = new InFlightRegistry();
        CountDownLatch renewed = new CountDownLatch(1);
        LeaseManager manager = new LeaseManager(
                wheel, registry, task -> {
                    renewed.countDown();
                    return true;
                }, Duration.ofMillis(10), task -> {
                }, Clock.fixed(NOW, ZoneOffset.UTC));
        LeaseTask task = task("m-1", NOW.plusSeconds(60));
        manager.start();
        try {
            manager.register(task);
            wheel.fire(task);

            assertTrue(renewed.await(1, TimeUnit.SECONDS));
            for (int i = 0; i < 100 && wheel.scheduleCount() < 2; i++) {
                Thread.sleep(10);
            }
            assertEquals(1, registry.size());
            assertEquals(2, wheel.scheduleCount());
        } finally {
            manager.complete(task);
            manager.close();
        }
    }

    @Test
    void renewFailure_removesTaskAndNotifies() throws Exception {
        ManualLeaseWheel wheel = new ManualLeaseWheel();
        InFlightRegistry registry = new InFlightRegistry();
        CountDownLatch lost = new CountDownLatch(1);
        LeaseManager manager = new LeaseManager(
                wheel, registry, task -> false, Duration.ofMillis(10),
                task -> lost.countDown(), Clock.fixed(NOW, ZoneOffset.UTC));
        LeaseTask task = task("m-1", NOW.plusSeconds(60));
        manager.start();
        try {
            manager.register(task);
            wheel.fire(task);

            assertTrue(lost.await(1, TimeUnit.SECONDS));
            assertEquals(0, registry.size());
        } finally {
            manager.close();
        }
    }

    @Test
    void expiredTask_stopsWithoutRenewing() throws Exception {
        ManualLeaseWheel wheel = new ManualLeaseWheel();
        InFlightRegistry registry = new InFlightRegistry();
        AtomicInteger renewals = new AtomicInteger();
        CountDownLatch lost = new CountDownLatch(1);
        LeaseManager manager = new LeaseManager(
                wheel, registry, task -> {
                    renewals.incrementAndGet();
                    return true;
                }, Duration.ofMillis(10), task -> lost.countDown(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        LeaseTask task = task("m-1", NOW.minusSeconds(1));
        manager.start();
        try {
            manager.register(task);
            wheel.fire(task);

            assertTrue(lost.await(1, TimeUnit.SECONDS));
            assertEquals(0, renewals.get());
            assertEquals(0, registry.size());
        } finally {
            manager.close();
        }
    }

    private LeaseTask task(String msgId, Instant maxDeadline) {
        return new LeaseTask(msgId, "lead", "c-1", "owner-1", 1, maxDeadline);
    }

    private static final class ManualLeaseWheel implements LeaseWheel {

        private final Map<String, Consumer<LeaseTask>> callbacks = new HashMap<>();
        private final AtomicInteger schedules = new AtomicInteger();

        @Override
        public void schedule(LeaseTask task, Duration delay, Consumer<LeaseTask> onDue) {
            schedules.incrementAndGet();
            callbacks.put(task.key(), onDue);
        }

        @Override
        public void cancel(LeaseTask task) {
            callbacks.remove(task.key());
        }

        @Override
        public void close() {
            callbacks.clear();
        }

        void fire(LeaseTask task) {
            Consumer<LeaseTask> callback = callbacks.get(task.key());
            if (callback != null) {
                callback.accept(task);
            }
        }

        int scheduleCount() {
            return schedules.get();
        }
    }
}
