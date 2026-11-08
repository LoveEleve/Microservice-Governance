package com.wuba.crm.tx.tcc.coordinator.persistence;

import java.time.LocalDateTime;

/**
 * 全局事务持久化接口。
 */
public interface TccGlobalRepository {

    /**
     * 按 Begin 幂等键查询。
     *
     * @param requestId 幂等键
     * @return 全局事务，不存在返回 null
     */
    TccGlobalEntity findByRequestId(String requestId);

    /**
     * 按 Begin 幂等键加行锁查询。
     *
     * @param requestId 幂等键
     * @return 全局事务，不存在返回 null
     */
    TccGlobalEntity findByRequestIdForUpdate(String requestId);

    /**
     * 按 XID 加行锁查询。
     *
     * @param xid 全局事务号
     * @return 全局事务，不存在返回 null
     */
    TccGlobalEntity findByXidForUpdate(long xid);

    /**
     * 插入全局事务。
     *
     * @param entity 全局事务
     * @return 影响行数
     */
    int insert(TccGlobalEntity entity);

    /**
     * 更新最大分支号。
     *
     * @param xid       全局事务号
     * @param branchSeq 新的最大分支号
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    int updateBranchSeq(long xid, long branchSeq, LocalDateTime updatedAt);
}
