package com.wuba.crm.tx.tcc.participant.barrier;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * barrier 仓储 MyBatis 实现。
 */
@Repository
public class MybatisBarrierRepository implements BarrierRepository {

    private final BarrierMapper mapper;

    /**
     * 创建仓储。
     *
     * @param mapper barrier Mapper
     */
    public MybatisBarrierRepository(BarrierMapper mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("mapper must not be null");
        }
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public BarrierEntity findForUpdate(long xid, long branchId) {
        return mapper.findForUpdate(xid, branchId);
    }

    /** {@inheritDoc} */
    @Override
    public int insert(BarrierEntity entity) {
        return mapper.insert(entity);
    }

    /** {@inheritDoc} */
    @Override
    public int markConfirmed(long xid, long branchId, LocalDateTime updatedAt) {
        return mapper.markConfirmed(xid, branchId, updatedAt);
    }

    /** {@inheritDoc} */
    @Override
    public int markCancelled(long xid, long branchId, LocalDateTime updatedAt) {
        return mapper.markCancelled(xid, branchId, updatedAt);
    }
}
