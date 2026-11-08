package com.wuba.crm.tx.tcc.participant.barrier;

import java.time.LocalDateTime;

/**
 * barrier 持久化接口。
 */
public interface BarrierRepository {

    /**
     * 加行锁查询。
     *
     * @param xid      全局事务号
     * @param branchId 分支号
     * @return barrier，不存在返回 null
     */
    BarrierEntity findForUpdate(long xid, long branchId);

    /**
     * 插入 barrier。
     *
     * @param entity barrier 实体
     * @return 影响行数
     */
    int insert(BarrierEntity entity);

    /**
     * TRIED -> CONFIRMED。
     *
     * @param xid       全局事务号
     * @param branchId  分支号
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    int markConfirmed(long xid, long branchId, LocalDateTime updatedAt);

    /**
     * TRIED -> CANCELLED。
     *
     * @param xid       全局事务号
     * @param branchId  分支号
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    int markCancelled(long xid, long branchId, LocalDateTime updatedAt);
}
