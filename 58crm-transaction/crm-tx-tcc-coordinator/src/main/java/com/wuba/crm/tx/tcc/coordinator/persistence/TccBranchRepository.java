package com.wuba.crm.tx.tcc.coordinator.persistence;

import java.time.LocalDateTime;

/**
 * 分支事务持久化接口。
 */
public interface TccBranchRepository {

    /**
     * 按分支幂等键查询。
     *
     * @param xid         全局事务号
     * @param resourceKey 分支幂等键
     * @return 分支，不存在返回 null
     */
    TccBranchEntity findByResourceKey(long xid, String resourceKey);

    /**
     * 按分支主键查询。
     *
     * @param xid      全局事务号
     * @param branchId 分支号
     * @return 分支，不存在返回 null
     */
    TccBranchEntity findByBranchId(long xid, long branchId);

    /**
     * 插入分支。
     *
     * @param entity 分支实体
     * @return 影响行数
     */
    int insert(TccBranchEntity entity);

    /**
     * 上报 Try 结果。
     *
     * @param xid       全局事务号
     * @param branchId  分支号
     * @param status    TRY_SUCCESS 或 TRY_FAIL
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    int reportTryResult(long xid, long branchId, String status, LocalDateTime updatedAt);
}
