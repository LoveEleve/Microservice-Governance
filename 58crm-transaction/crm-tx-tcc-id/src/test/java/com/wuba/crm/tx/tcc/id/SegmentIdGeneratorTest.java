package com.wuba.crm.tx.tcc.id;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证双缓冲发号、批量取号、未知 key 和三档步长。
 */
class SegmentIdGeneratorTest {

    @Test
    void firstIdStartsFromZero() {
        SegmentIdGenerator generator = generator(new InMemorySegmentAllocator(), null);
        try {
            generator.register("tcc-xid");
            assertEquals(0L, generator.nextId("tcc-xid"));
            assertEquals(1L, generator.nextId("tcc-xid"));
            assertEquals(2L, generator.nextId("tcc-xid"));
        } finally {
            generator.close();
        }
    }

    @Test
    void unknownKeyReturnsCode() {
        SegmentIdGenerator generator = generator(new InMemorySegmentAllocator(), null);
        try {
            assertEquals(SegmentIdGenerator.KEY_NOT_EXISTS, generator.nextId("missing"));
        } finally {
            generator.close();
        }
    }

    @Test
    void batchIdsAreContinuous() {
        SegmentIdGenerator generator = generator(new InMemorySegmentAllocator(), null);
        try {
            generator.register("tcc-xid");
            long[] ids = generator.nextIds("tcc-xid", 10);
            assertEquals(10, ids.length);
            for (int i = 0; i < ids.length; i++) {
                assertEquals(i, ids[i]);
            }
        } finally {
            generator.close();
        }
    }

    @Test
    void concurrentIdsAreUnique() throws Exception {
        SegmentIdGenerator generator = generator(new InMemorySegmentAllocator(), null);
        generator.register("tcc-xid");
        int threads = 8;
        int perThread = 500;
        ConcurrentLinkedQueue<Long> ids = new ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        try {
            for (int t = 0; t < threads; t++) {
                new Thread(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            long id = SegmentIdGenerator.BOTH_SEGMENTS_NULL;
                            for (int retry = 0; retry < 10_000 && id < 0; retry++) {
                                id = generator.nextId("tcc-xid");
                            }
                            ids.add(id);
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }, "segment-test-" + t).start();
            }

            start.countDown();
            assertTrue(done.await(60, TimeUnit.SECONDS), "generator workers timed out");
            Set<Long> unique = new HashSet<>(ids);
            assertEquals(threads * perThread, ids.size());
            assertEquals(threads * perThread, unique.size(), "duplicate ids detected");
            assertTrue(unique.stream().allMatch(id -> id >= 0));
        } finally {
            generator.close();
        }
    }

    @Test
    void nextStep_doublesWhenFast() {
        MutableClock clock = new MutableClock(1_000_000L);
        SegmentIdGenerator generator = generator(new InMemorySegmentAllocator(), clock);
        try {
            SegmentBuffer buffer = new SegmentBuffer();
            buffer.setStep(100);
            buffer.setMinStep(100);
            buffer.setUpdateTimestamp(clock.millis.get() - TimeUnit.MINUTES.toMillis(5));
            assertEquals(200, generator.nextStep(buffer));
        } finally {
            generator.close();
        }
    }

    @Test
    void nextStep_halvesButKeepsFloor() {
        MutableClock clock = new MutableClock(1_000_000L);
        SegmentIdGenerator generator = generator(new InMemorySegmentAllocator(), clock);
        try {
            SegmentBuffer buffer = new SegmentBuffer();
            buffer.setStep(200);
            buffer.setMinStep(100);
            buffer.setUpdateTimestamp(clock.millis.get() - TimeUnit.MINUTES.toMillis(60));
            assertEquals(100, generator.nextStep(buffer));
            buffer.setStep(100);
            assertEquals(100, generator.nextStep(buffer));
        } finally {
            generator.close();
        }
    }

    @Test
    void watermark_reportsCurrentRange() {
        SegmentIdGenerator generator = generator(new InMemorySegmentAllocator(), null);
        try {
            generator.register("tcc-xid");
            generator.nextId("tcc-xid");
            SegmentWatermark watermark = generator.watermark("tcc-xid");
            assertEquals("tcc-xid", watermark.bizType());
            assertEquals(1L, watermark.value());
            assertTrue(watermark.max() > watermark.value());
        } finally {
            generator.close();
        }
    }

    private SegmentIdGenerator generator(SegmentAllocator allocator, MutableClock clock) {
        return new SegmentIdGenerator(
                10,
                10_000,
                TimeUnit.MINUTES.toMillis(15),
                0.9,
                allocator,
                clock == null ? Clock.systemUTC() : clock);
    }

    /**
     * 可调时钟，用于测试三档步长。
     */
    private static final class MutableClock extends Clock {

        private final AtomicLong millis;

        private MutableClock(long startMillis) {
            this.millis = new AtomicLong(startMillis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }
    }
}
