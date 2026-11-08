package com.wuba.crm.tx.tcc.id;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 MyBatis-Plus 的号段分配器。
 *
 * <p>分配动作必须在独立事务里完成：</p>
 *
 * <pre>
 * UPDATE id_segment SET max_id = max_id + step WHERE biz_type = ?
 * SELECT max_id FROM id_segment WHERE biz_type = ?
 * COMMIT
 * </pre>
 *
 * <p>两条语句必须在同一个连接内执行。这里用
 * {@link Transactional} 的 {@code REQUIRES_NEW} 保证独立连接和独立提交，
 * 避免号段分配被外层业务事务回滚，也避免连接池把两条 SQL 分到不同连接。</p>
 *
 * <p>重要：该类必须由 Spring 容器创建（或通过事务代理调用），
 * 直接 {@code new MybatisSegmentAllocator(mapper)} 不会触发
 * {@code @Transactional}。</p>
 */
public class MybatisSegmentAllocator implements SegmentAllocator {

    private final IdSegmentMapper mapper;

    /**
     * 创建分配器。
     *
     * @param mapper id_segment Mapper，不能为空
     */
    public MybatisSegmentAllocator(IdSegmentMapper mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("mapper must not be null");
        }
        this.mapper = mapper;
    }

    /**
     * 在独立事务内原子领取号段。
     *
     * @param bizType 业务 key，例如 {@code tcc-xid}
     * @param step    本次领取数量，必须大于 0
     * @return 领取后的最大水位
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public long allocateMaxId(String bizType, int step) {
        if (bizType == null || bizType.isBlank()) {
            throw new IllegalArgumentException("bizType must not be blank");
        }
        if (step <= 0) {
            throw new IllegalArgumentException("step must be positive");
        }

        int updated = mapper.increaseMaxId(bizType, step);
        if (updated != 1) {
            throw new IllegalStateException("id_segment not found: " + bizType);
        }

        Long maxId = mapper.selectMaxId(bizType);
        if (maxId == null) {
            throw new IllegalStateException("id_segment disappeared: " + bizType);
        }
        return maxId;
    }

    /**
     * 读取配置步长。
     *
     * @param bizType 业务 key
     * @return 配置步长；不存在时返回 0
     */
    @Override
    public int configuredStep(String bizType) {
        Integer step = mapper.selectStep(bizType);
        return step == null ? 0 : step;
    }
}
