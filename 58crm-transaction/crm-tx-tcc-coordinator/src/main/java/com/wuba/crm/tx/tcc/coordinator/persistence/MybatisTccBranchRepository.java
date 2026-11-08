package com.wuba.crm.tx.tcc.coordinator.persistence;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 基于 MyBatis 的分支事务仓储实现。
 */
@Repository
public class MybatisTccBranchRepository implements TccBranchRepository {

    private final TccBranchMapper mapper;

    /**
     * 创建仓储。
     *
     * @param mapper 分支事务 Mapper
     */
    public MybatisTccBranchRepository(TccBranchMapper mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("mapper must not be null");
        }
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public TccBranchEntity findByResourceKey(long xid, String resourceKey) {
        return mapper.findByResourceKey(xid, resourceKey);
    }

    /** {@inheritDoc} */
    @Override
    public TccBranchEntity findByBranchId(long xid, long branchId) {
        return mapper.findByBranchId(xid, branchId);
    }

    /** {@inheritDoc} */
    @Override
    public int insert(TccBranchEntity entity) {
        return mapper.insert(entity);
    }

    /** {@inheritDoc} */
    @Override
    public int reportTryResult(long xid, long branchId, String status, LocalDateTime updatedAt) {
        return mapper.reportTryResult(xid, branchId, status, updatedAt);
    }
}
