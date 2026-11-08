package com.wuba.crm.tx.tcc.id;

/**
 * 号段分配器：负责从持久化存储中领取一段连续 ID。
 *
 * <p>返回的 {@code maxId} 是本次领取后的水位，合法区间为
 * {@code [maxId - step, maxId)}。调用方必须保证同一业务 key 的领取操作
 * 在存储侧是原子的。</p>
 */
public interface SegmentAllocator {

    /**
     * 领取一个号段。
     *
     * @param bizType 业务 key，例如 {@code tcc-xid}
     * @param step    本次领取的 ID 数量，必须大于 0
     * @return 领取后的最大水位，调用方据此计算区间
     */
    long allocateMaxId(String bizType, int step);

    /**
     * 返回存储中配置的步长，作为自适应步长的下限。
     *
     * @param bizType 业务 key
     * @return 配置步长；0 表示没有下限
     */
    default int configuredStep(String bizType) {
        return 0;
    }
}
