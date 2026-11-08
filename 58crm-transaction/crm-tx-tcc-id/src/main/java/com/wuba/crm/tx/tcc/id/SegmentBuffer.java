package com.wuba.crm.tx.tcc.id;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 双缓冲号段容器。
 *
 * <p>发号路径持有读锁，号段切换持有写锁；这样同一时刻只有一个线程能
 * 切换 current，而多个线程可以并发从当前号段取号。</p>
 */
public final class SegmentBuffer {

    private final Segment[] segments;
    private volatile int currentPos;
    private volatile boolean nextReady;
    private volatile boolean initOk;
    private final AtomicBoolean threadRunning = new AtomicBoolean(false);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile int step;
    private volatile int minStep;
    private volatile long updateTimestamp;

    /**
     * 创建空的双缓冲，初始 current 为 0。
     */
    public SegmentBuffer() {
        this.segments = new Segment[]{new Segment(this), new Segment(this)};
    }

    /**
     * 返回两个物理号段。
     *
     * @return 号段数组，长度固定为 2
     */
    public Segment[] getSegments() {
        return segments;
    }

    /**
     * 当前正在发号的号段。
     *
     * @return 当前号段
     */
    public Segment getCurrent() {
        return segments[currentPos];
    }

    /**
     * 下一个待切换号段的位置。
     *
     * @return 下一个位置
     */
    public int nextPos() {
        return (currentPos + 1) % 2;
    }

    /**
     * 在当前号和备用号之间切换。
     */
    public void switchPos() {
        currentPos = nextPos();
    }

    /**
     * 备用号段是否已经加载完成。
     *
     * @return true 表示可切换
     */
    public boolean isNextReady() {
        return nextReady;
    }

    /**
     * 标记备用号段加载状态。
     *
     * @param nextReady 是否就绪
     */
    public void setNextReady(boolean nextReady) {
        this.nextReady = nextReady;
    }

    /**
     * 第一个号段是否初始化完成。
     *
     * @return true 表示已初始化
     */
    public boolean isInitOk() {
        return initOk;
    }

    /**
     * 标记初始化状态。
     *
     * @param initOk 是否初始化完成
     */
    public void setInitOk(boolean initOk) {
        this.initOk = initOk;
    }

    /**
     * 预取线程运行标记，保证同一时刻只有一个预取任务。
     *
     * @return 运行标记
     */
    public AtomicBoolean getThreadRunning() {
        return threadRunning;
    }

    /**
     * 读锁，发号路径使用。
     *
     * @return 读锁
     */
    public Lock rLock() {
        return lock.readLock();
    }

    /**
     * 写锁，号段切换使用。
     *
     * @return 写锁
     */
    public Lock wLock() {
        return lock.writeLock();
    }

    /**
     * 当前步长。
     *
     * @return 步长
     */
    public int getStep() {
        return step;
    }

    /**
     * 设置当前步长。
     *
     * @param step 步长
     */
    public void setStep(int step) {
        this.step = step;
    }

    /**
     * 自适应步长下限。
     *
     * @return 下限
     */
    public int getMinStep() {
        return minStep;
    }

    /**
     * 设置自适应步长下限。
     *
     * @param minStep 下限
     */
    public void setMinStep(int minStep) {
        this.minStep = minStep;
    }

    /**
     * 上一次加载号段的时间戳，用于自适应步长。
     *
     * @return 时间戳毫秒
     */
    public long getUpdateTimestamp() {
        return updateTimestamp;
    }

    /**
     * 设置上一次加载号段的时间戳。
     *
     * @param updateTimestamp 时间戳毫秒
     */
    public void setUpdateTimestamp(long updateTimestamp) {
        this.updateTimestamp = updateTimestamp;
    }
}
