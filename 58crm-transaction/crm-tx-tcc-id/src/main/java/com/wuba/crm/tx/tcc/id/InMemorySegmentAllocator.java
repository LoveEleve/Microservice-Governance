package com.wuba.crm.tx.tcc.id;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存号段分配器，供单元测试使用。
 *
 * <p>它模拟数据库中的 {@code max_id} 水位，不提供跨进程原子性；
 * 生产环境必须使用 {@link MybatisSegmentAllocator}。</p>
 */
public final class InMemorySegmentAllocator implements SegmentAllocator {

    private final ConcurrentMap<String, AtomicLong> maxIds = new ConcurrentHashMap<>();

    /**
     * 原子推进内存水位并返回本次领取后的最大值。
     *
     * @param bizType 业务 key
     * @param step    本次领取数量
     * @return 领取后的水位
     */
    @Override
    public long allocateMaxId(String bizType, int step) {
        return maxIds.computeIfAbsent(bizType, ignored -> new AtomicLong())
                .addAndGet(step);
    }
}
