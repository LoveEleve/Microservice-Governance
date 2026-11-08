package com.wuba.crm.tx.tcc.id;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 单个号段，表示左闭右开的 ID 区间 {@code [value, max)}。
 *
 * <p>{@code value} 使用 {@link AtomicLong}，因此同一号段内的取号不需要
 * 加锁；号段切换由 {@link SegmentBuffer} 负责。</p>
 */
public final class Segment {

    private final AtomicLong value = new AtomicLong();
    private volatile long max;
    private volatile int step;
    private final SegmentBuffer buffer;

    /**
     * 创建属于指定双缓冲的号段。
     *
     * @param buffer 所属的双缓冲
     */
    public Segment(SegmentBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("buffer must not be null");
        }
        this.buffer = buffer;
    }

    /**
     * 当前可分配的游标。
     *
     * @return 号段游标
     */
    public AtomicLong getValue() {
        return value;
    }

    /**
     * 号段上界，不包含。
     *
     * @return 上界
     */
    public long getMax() {
        return max;
    }

    /**
     * 设置号段上界。
     *
     * @param max 上界
     */
    public void setMax(long max) {
        this.max = max;
    }

    /**
     * 领号时使用的步长。
     *
     * @return 步长
     */
    public int getStep() {
        return step;
    }

    /**
     * 设置领号步长。
     *
     * @param step 步长
     */
    public void setStep(int step) {
        this.step = step;
    }

    /**
     * 所属双缓冲。
     *
     * @return 双缓冲
     */
    public SegmentBuffer getBuffer() {
        return buffer;
    }

    /**
     * 当前号段剩余数量，用于触发预取。
     *
     * @return 剩余数量
     */
    public long getIdle() {
        return max - value.get();
    }
}
