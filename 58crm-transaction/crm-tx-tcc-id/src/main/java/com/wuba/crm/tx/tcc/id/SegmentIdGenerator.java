package com.wuba.crm.tx.tcc.id;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * 号段发号器：双 buffer + 后台预取 + 三档步长自适应。
 *
 * <p>发号路径从内存号段取号，只有号段剩余量低于阈值时才异步访问数据库。
 * 这样 XID 生成不会每一次都访问 MySQL，同时仍能通过数据库水位保证多个
 * TC 实例之间不重复。</p>
 *
 * <p>返回值语义沿用号段模型的异常码：{@link #KEY_NOT_EXISTS} 表示调用方
 * 没有注册业务 key，{@link #BOTH_SEGMENTS_NULL} 表示背压，调用方可以短暂
 * 重试。生产代码通常通过 {@link TccXidGenerator} 把它转换成异常。</p>
 */
public final class SegmentIdGenerator implements AutoCloseable {

    private static final Logger log = Logger.getLogger(SegmentIdGenerator.class.getName());

    /** 未初始化时的返回码。 */
    public static final long NOT_INIT = -1L;

    /** 业务 key 未注册时的返回码。 */
    public static final long KEY_NOT_EXISTS = -2L;

    /** 两个号段都不可用时的背压返回码。 */
    public static final long BOTH_SEGMENTS_NULL = -3L;

    private static final AtomicInteger THREAD_NUM = new AtomicInteger();

    private final int defaultStep;
    private final int maxStep;
    private final long segmentDurationMillis;
    private final double prefetchThreshold;
    private final SegmentAllocator allocator;
    private final Clock clock;
    private final ExecutorService padService;
    private final Map<String, SegmentBuffer> cache = new ConcurrentHashMap<>();

    /**
     * 创建发号器，使用系统时钟。
     *
     * @param defaultStep           初始步长
     * @param maxStep               自适应最大步长
     * @param segmentDurationMillis 期望一个号段使用的时间
     * @param prefetchThreshold     剩余比例低于该值时预取
     * @param allocator             号段分配器
     */
    public SegmentIdGenerator(
            int defaultStep,
            int maxStep,
            long segmentDurationMillis,
            double prefetchThreshold,
            SegmentAllocator allocator) {
        this(defaultStep, maxStep, segmentDurationMillis, prefetchThreshold,
                allocator, Clock.systemUTC());
    }

    /**
     * 创建发号器，允许测试注入时钟。
     *
     * @param defaultStep           初始步长
     * @param maxStep               自适应最大步长
     * @param segmentDurationMillis 期望一个号段使用的时间
     * @param prefetchThreshold     剩余比例低于该值时预取
     * @param allocator             号段分配器
     * @param clock                 时钟
     */
    SegmentIdGenerator(
            int defaultStep,
            int maxStep,
            long segmentDurationMillis,
            double prefetchThreshold,
            SegmentAllocator allocator,
            Clock clock) {
        if (defaultStep <= 0) {
            throw new IllegalArgumentException("defaultStep must be positive");
        }
        if (maxStep < defaultStep) {
            throw new IllegalArgumentException("maxStep must be >= defaultStep");
        }
        if (segmentDurationMillis <= 0) {
            throw new IllegalArgumentException("segmentDurationMillis must be positive");
        }
        if (prefetchThreshold <= 0 || prefetchThreshold >= 1) {
            throw new IllegalArgumentException("prefetchThreshold must be between 0 and 1");
        }
        if (allocator == null) {
            throw new IllegalArgumentException("allocator must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }

        this.defaultStep = defaultStep;
        this.maxStep = maxStep;
        this.segmentDurationMillis = segmentDurationMillis;
        this.prefetchThreshold = prefetchThreshold;
        this.allocator = allocator;
        this.clock = clock;
        this.padService = new ThreadPoolExecutor(
                2,
                8,
                60L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable,
                            "tcc-id-segment-pad-" + THREAD_NUM.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /**
     * 注册业务 key。首次发号时才真正加载号段。
     *
     * @param bizType 业务 key
     */
    public void register(String bizType) {
        if (bizType == null || bizType.isBlank()) {
            throw new IllegalArgumentException("bizType must not be blank");
        }
        cache.computeIfAbsent(bizType, ignored -> new SegmentBuffer());
    }

    /**
     * 取一个 ID。
     *
     * @param bizType 业务 key
     * @return ID；负数是异常码
     */
    public long nextId(String bizType) {
        SegmentBuffer buffer = cache.get(bizType);
        if (buffer == null) {
            return KEY_NOT_EXISTS;
        }
        ensureInit(bizType, buffer);
        return takeFromBuffer(bizType, buffer);
    }

    /**
     * 批量取 ID。当前号段不够时退化为逐个取。
     *
     * @param bizType 业务 key
     * @param count   数量
     * @return ID 数组；第一个元素可能是负异常码
     */
    public long[] nextIds(String bizType, int count) {
        if (count <= 0) {
            return new long[0];
        }
        SegmentBuffer buffer = cache.get(bizType);
        if (buffer == null) {
            return new long[]{KEY_NOT_EXISTS};
        }
        ensureInit(bizType, buffer);

        long[] result = new long[count];
        int filled = 0;
        while (filled < count) {
            int want = count - filled;
            buffer.rLock().lock();
            try {
                Segment segment = buffer.getCurrent();
                long start = segment.getValue().get();
                if (start + want <= segment.getMax()) {
                    long got = segment.getValue().getAndAdd(want);
                    if (got + want <= segment.getMax()) {
                        for (int i = 0; i < want; i++) {
                            result[filled + i] = got + i;
                        }
                        return result;
                    }
                }
            } finally {
                buffer.rLock().unlock();
            }

            long id = takeFromBuffer(bizType, buffer);
            if (id < 0) {
                result[filled] = id;
                return result;
            }
            result[filled++] = id;
        }
        return result;
    }

    /**
     * 查看号段水位。
     *
     * @param bizType 业务 key
     * @return 水位快照
     */
    public SegmentWatermark watermark(String bizType) {
        SegmentBuffer buffer = cache.get(bizType);
        if (buffer == null) {
            return new SegmentWatermark(bizType, 0, 0, 0, 0, false);
        }
        Segment segment = buffer.getCurrent();
        return new SegmentWatermark(bizType, segment.getValue().get(), segment.getMax(),
                segment.getStep(), segment.getIdle(), buffer.isNextReady());
    }

    private void ensureInit(String bizType, SegmentBuffer buffer) {
        if (buffer.isInitOk()) {
            return;
        }
        synchronized (buffer) {
            if (!buffer.isInitOk()) {
                loadSegment(bizType, buffer.getCurrent(), defaultStep);
                buffer.setInitOk(true);
            }
        }
    }

    private long takeFromBuffer(String bizType, SegmentBuffer buffer) {
        while (true) {
            buffer.rLock().lock();
            try {
                Segment segment = buffer.getCurrent();
                if (!buffer.isNextReady()
                        && segment.getIdle() < prefetchThreshold * segment.getStep()
                        && buffer.getThreadRunning().compareAndSet(false, true)) {
                    padService.execute(() -> padNext(bizType, buffer));
                }
                long value = segment.getValue().getAndIncrement();
                if (value < segment.getMax()) {
                    return value;
                }
            } finally {
                buffer.rLock().unlock();
            }

            waitPad(buffer);
            buffer.wLock().lock();
            try {
                Segment segment = buffer.getCurrent();
                long value = segment.getValue().getAndIncrement();
                if (value < segment.getMax()) {
                    return value;
                }
                if (buffer.isNextReady()) {
                    buffer.switchPos();
                    buffer.setNextReady(false);
                } else {
                    log.severe("Both segments not ready: " + bizType);
                    return BOTH_SEGMENTS_NULL;
                }
            } finally {
                buffer.wLock().unlock();
            }
        }
    }

    private void padNext(String bizType, SegmentBuffer buffer) {
        boolean loaded = false;
        try {
            loadSegment(bizType, buffer.getSegments()[buffer.nextPos()], nextStep(buffer));
            loaded = true;
        } catch (RuntimeException failure) {
            log.warning("Pad segment failed for " + bizType + ": " + failure.getMessage());
        } finally {
            if (loaded) {
                buffer.wLock().lock();
                try {
                    buffer.setNextReady(true);
                } finally {
                    buffer.getThreadRunning().set(false);
                    buffer.wLock().unlock();
                }
            } else {
                buffer.getThreadRunning().set(false);
            }
        }
    }

    /**
     * 三档步长：吃得太快翻倍，正常不变，吃得太慢减半但不低于配置下限。
     *
     * @param buffer 号段缓冲
     * @return 下一次领号使用的步长
     */
    int nextStep(SegmentBuffer buffer) {
        long duration = clock.millis() - buffer.getUpdateTimestamp();
        int next = buffer.getStep();
        if (duration < segmentDurationMillis) {
            if (next * 2 <= maxStep) {
                next *= 2;
            }
        } else if (duration < segmentDurationMillis * 2) {
            // 正常速度，步长不变。
        } else {
            int floor = buffer.getMinStep() > 0 ? buffer.getMinStep() : defaultStep;
            next = next / 2 >= floor ? next / 2 : next;
        }
        return next;
    }

    private void loadSegment(String bizType, Segment segment, int step) {
        long maxId = allocator.allocateMaxId(bizType, step);
        if (maxId < step) {
            throw new IllegalStateException("invalid segment maxId=" + maxId + " step=" + step);
        }
        // 先设置起点，再设置终点，避免其他线程读到半初始化号段。
        segment.getValue().set(maxId - step);
        segment.setMax(maxId);
        segment.setStep(step);

        SegmentBuffer buffer = segment.getBuffer();
        buffer.setUpdateTimestamp(clock.millis());
        buffer.setStep(step);
        buffer.setMinStep(Math.max(allocator.configuredStep(bizType), 0));
    }

    private void waitPad(SegmentBuffer buffer) {
        int spin = 0;
        while (buffer.getThreadRunning().get()) {
            if (++spin > 10_000) {
                try {
                    TimeUnit.MILLISECONDS.sleep(10);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                break;
            }
        }
    }

    /**
     * 停止后台预取线程。
     */
    @Override
    public void close() {
        padService.shutdownNow();
    }
}
