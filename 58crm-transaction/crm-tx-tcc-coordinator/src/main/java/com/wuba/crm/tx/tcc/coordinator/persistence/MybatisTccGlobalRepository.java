package com.wuba.crm.tx.tcc.coordinator.persistence;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 基于 MyBatis 的全局事务仓储实现。
 */
@Repository
public class MybatisTccGlobalRepository implements TccGlobalRepository {

    private final TccGlobalMapper mapper;

    /**
     * 创建仓储。
     *
     * @param mapper 全局事务 Mapper
     */
    public MybatisTccGlobalRepository(TccGlobalMapper mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("mapper must not be null");
        }
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public TccGlobalEntity findByRequestId(String requestId) {
        return mapper.findByRequestId(requestId);
    }

    /** {@inheritDoc} */
    @Override
    public TccGlobalEntity findByRequestIdForUpdate(String requestId) {
        return mapper.findByRequestIdForUpdate(requestId);
    }

    /** {@inheritDoc} */
    @Override
    public TccGlobalEntity findByXidForUpdate(long xid) {
        return mapper.findByXidForUpdate(xid);
    }

    /** {@inheritDoc} */
    @Override
    public TccGlobalEntity findByXid(long xid) {
        return mapper.findByXid(xid);
    }

    /** {@inheritDoc} */
    @Override
    public int claimOwner(long xid, String owner, LocalDateTime leaseUntil, LocalDateTime now) {
        return mapper.claimOwner(xid, owner, leaseUntil, now);
    }

    /** {@inheritDoc} */
    @Override
    public int renewOwner(long xid, String owner, LocalDateTime leaseUntil, LocalDateTime now) {
        return mapper.renewOwner(xid, owner, leaseUntil, now);
    }

    /** {@inheritDoc} */
    @Override
    public int insert(TccGlobalEntity entity) {
        return mapper.insert(entity);
    }

    /** {@inheritDoc} */
    @Override
    public int updateBranchSeq(long xid, long branchSeq, LocalDateTime updatedAt) {
        return mapper.updateBranchSeq(xid, branchSeq, updatedAt);
    }

    /** {@inheritDoc} */
    @Override
    public int transitionWithOwner(long xid, String expected, String target, String owner,
                                   LocalDateTime leaseUntil, LocalDateTime now) {
        return mapper.transitionWithOwner(xid, expected, target, owner, leaseUntil, now);
    }

    /** {@inheritDoc} */
    @Override
    public int transitionOwned(long xid, String expected, String target, String owner,
                               LocalDateTime now) {
        return mapper.transitionOwned(xid, expected, target, owner, now);
    }
}
