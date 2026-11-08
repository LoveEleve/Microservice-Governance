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
     * 查询全局事务下所有分支。
     *
     * @param xid 全局事务号
     * @return 分支列表
     */
    java.util.List<TccBranchEntity> findByXid(long xid);

    /**
     * 查询到期待重试的 XID。
     *
     * @param now   当前时间
     * @param limit 最大数量
     * @return XID 列表
     */
    java.util.List<Long> findDueRetryXids(java.time.LocalDateTime now, int limit);

    /**
     * 记录持久化重试。
     *
     * @param xid        全局事务号
     * @param branchId   分支号
     * @param status     分支状态
     * @param retryCount 新重试次数
     * @param nextRetry  下次重试时间
     * @param owner      owner token
     * @param now        当前时间
     * @return 影响行数
     */
    int scheduleRetry(long xid, long branchId, String status, int retryCount,
                      java.time.LocalDateTime nextRetry, String owner,
                      java.time.LocalDateTime now);

    /**
     * TRY_SUCCESS -> CONFIRMING。
     *
     * @param xid       全局事务号
     * @param nextRetry 下次重试时间
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    int updateToConfirming(long xid, java.time.LocalDateTime nextRetry,
                           java.time.LocalDateTime updatedAt);

    /**
     * 可取消分支 -> CANCELLING。
     *
     * @param xid       全局事务号
     * @param nextRetry 下次重试时间
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    int updateToCancelling(long xid, java.time.LocalDateTime nextRetry,
                           java.time.LocalDateTime updatedAt);

    /**
     * 标记 Confirm 成功。
     *
     * @param xid      全局事务号
     * @param branchId 分支号
     * @param owner    owner token
     * @param now      当前时间
     * @return 影响行数
     */
    int markConfirmed(long xid, long branchId, String owner, java.time.LocalDateTime now);

    /**
     * 标记 Cancel 成功。
     *
     * @param xid      全局事务号
     * @param branchId 分支号
     * @param owner    owner token
     * @param now      当前时间
     * @return 影响行数
     */
    int markCancelled(long xid, long branchId, String owner, java.time.LocalDateTime now);

    /**
     * 标记永久失败。
     *
     * @param xid      全局事务号
     * @param branchId 分支号
     * @param owner    owner token
     * @param now      当前时间
     * @return 影响行数
     */
    int markDead(long xid, long branchId, String owner, java.time.LocalDateTime now);

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
