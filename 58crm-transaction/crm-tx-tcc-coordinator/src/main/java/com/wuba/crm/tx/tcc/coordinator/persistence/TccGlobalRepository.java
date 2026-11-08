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
     * 按 XID 普通查询。
     *
     * @param xid 全局事务号
     * @return 全局事务，不存在返回 null
     */
    TccGlobalEntity findByXid(long xid);

    /**
     * 抢占 owner。
     *
     * @param xid        全局事务号
     * @param owner      owner token
     * @param leaseUntil 租约到期时间
     * @param now        当前时间
     * @return 影响行数
     */
    int claimOwner(long xid, String owner, java.time.LocalDateTime leaseUntil,
                   java.time.LocalDateTime now);

    /**
     * 续租 owner。
     *
     * @param xid        全局事务号
     * @param owner      owner token
     * @param leaseUntil 租约到期时间
     * @param now        当前时间
     * @return 影响行数
     */
    int renewOwner(long xid, String owner, java.time.LocalDateTime leaseUntil,
                   java.time.LocalDateTime now);

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

    /**
     * 带 owner 推进全局状态。
     *
     * @param xid        全局事务号
     * @param expected   期望状态
     * @param target     目标状态
     * @param owner      owner token
     * @param leaseUntil 租约到期时间
     * @param now        当前时间
     * @return 影响行数
     */
    int transitionWithOwner(long xid, String expected, String target, String owner,
                            LocalDateTime leaseUntil, LocalDateTime now);

    /**
     * 带 owner 推进终态。
     *
     * @param xid      全局事务号
     * @param expected 期望状态
     * @param target   终态
     * @param owner    owner token
     * @param now      当前时间
     * @return 影响行数
     */
    int transitionOwned(long xid, String expected, String target, String owner,
                        LocalDateTime now);
}
