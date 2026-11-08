package com.wuba.crm.tx.tcc.coordinator.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * {@code t_tcc_branch} 的 MyBatis Mapper。
 *
 * <p>复合主键不适合 MyBatis-Plus 的单主键模型，这里使用显式 SQL；
 * 分支状态更新仍在数据库事务内完成。</p>
 */
@Mapper
public interface TccBranchMapper {

    /**
     * 按分支幂等键查询。
     *
     * @param xid         全局事务号
     * @param resourceKey 分支幂等键
     * @return 分支；不存在返回 null
     */
    @Select("""
            SELECT xid, branch_id AS branchId, resource_key AS resourceKey,
                   action_name AS actionName, callback_endpoint AS callbackEndpoint,
                   status, retry_count AS retryCount, next_retry_at AS nextRetryAt,
                   application_data AS applicationData,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM t_tcc_branch
            WHERE xid = #{xid} AND resource_key = #{resourceKey}
            """)
    TccBranchEntity findByResourceKey(
            @Param("xid") long xid,
            @Param("resourceKey") String resourceKey);

    /**
     * 按分支主键查询。
     *
     * @param xid      全局事务号
     * @param branchId 分支号
     * @return 分支；不存在返回 null
     */
    @Select("""
            SELECT xid, branch_id AS branchId, resource_key AS resourceKey,
                   action_name AS actionName, callback_endpoint AS callbackEndpoint,
                   status, retry_count AS retryCount, next_retry_at AS nextRetryAt,
                   application_data AS applicationData,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM t_tcc_branch
            WHERE xid = #{xid} AND branch_id = #{branchId}
            """)
    TccBranchEntity findByBranchId(
            @Param("xid") long xid,
            @Param("branchId") long branchId);

    /**
     * 查询一个全局事务下的所有分支。
     *
     * @param xid 全局事务号
     * @return 分支列表
     */
    @Select("""
            SELECT xid, branch_id AS branchId, resource_key AS resourceKey,
                   action_name AS actionName, callback_endpoint AS callbackEndpoint,
                   status, retry_count AS retryCount, next_retry_at AS nextRetryAt,
                   application_data AS applicationData,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM t_tcc_branch
            WHERE xid = #{xid}
            ORDER BY branch_id
            """)
    java.util.List<TccBranchEntity> findByXid(@Param("xid") long xid);

    /**
     * 二阶段开始时把分支从 TRY_SUCCESS 推到 CONFIRMING。
     *
     * @param xid       全局事务号
     * @param nextRetry 下次重试时间
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    @Update("""
            UPDATE t_tcc_branch
            SET status = 'CONFIRMING', next_retry_at = #{nextRetry}, updated_at = #{updatedAt}
            WHERE xid = #{xid}
              AND status = 'TRY_SUCCESS'
            """)
    int updateToConfirming(
            @Param("xid") long xid,
            @Param("nextRetry") LocalDateTime nextRetry,
            @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 回滚开始时把可取消分支推到 CANCELLING。
     *
     * @param xid       全局事务号
     * @param nextRetry 下次重试时间
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    @Update("""
            UPDATE t_tcc_branch
            SET status = 'CANCELLING', next_retry_at = #{nextRetry}, updated_at = #{updatedAt}
            WHERE xid = #{xid}
              AND status IN ('TRYING','TRY_SUCCESS','TRY_FAIL')
            """)
    int updateToCancelling(
            @Param("xid") long xid,
            @Param("nextRetry") LocalDateTime nextRetry,
            @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Confirm 成功后标记分支终态。
     *
     * @param xid       全局事务号
     * @param branchId  分支号
     * @param owner     owner token
     * @param now       当前时间
     * @return 影响行数
     */
    @Update("""
            UPDATE t_tcc_branch
            SET status = 'CONFIRMED', updated_at = #{now}
            WHERE xid = #{xid}
              AND branch_id = #{branchId}
              AND status = 'CONFIRMING'
              AND EXISTS (
                  SELECT 1 FROM t_tcc_global g
                  WHERE g.xid = #{xid}
                    AND g.status = 'CONFIRMING'
                    AND g.owner_token = #{owner}
                    AND g.lease_until > #{now}
              )
            """)
    int markConfirmed(
            @Param("xid") long xid,
            @Param("branchId") long branchId,
            @Param("owner") String owner,
            @Param("now") LocalDateTime now);

    /**
     * Cancel 成功后标记分支终态。
     *
     * @param xid       全局事务号
     * @param branchId  分支号
     * @param owner     owner token
     * @param now       当前时间
     * @return 影响行数
     */
    @Update("""
            UPDATE t_tcc_branch
            SET status = 'CANCELLED', updated_at = #{now}
            WHERE xid = #{xid}
              AND branch_id = #{branchId}
              AND status = 'CANCELLING'
              AND EXISTS (
                  SELECT 1 FROM t_tcc_global g
                  WHERE g.xid = #{xid}
                    AND g.status = 'CANCELLING'
                    AND g.owner_token = #{owner}
                    AND g.lease_until > #{now}
              )
            """)
    int markCancelled(
            @Param("xid") long xid,
            @Param("branchId") long branchId,
            @Param("owner") String owner,
            @Param("now") LocalDateTime now);

    /**
     * 标记分支永久失败。
     *
     * @param xid       全局事务号
     * @param branchId  分支号
     * @param owner     owner token
     * @param now       当前时间
     * @return 影响行数
     */
    @Update("""
            UPDATE t_tcc_branch
            SET status = 'DEAD', updated_at = #{now}
            WHERE xid = #{xid}
              AND branch_id = #{branchId}
              AND status IN ('CONFIRMING','CANCELLING')
              AND EXISTS (
                  SELECT 1 FROM t_tcc_global g
                  WHERE g.xid = #{xid}
                    AND g.status IN ('CONFIRMING','CANCELLING')
                    AND g.owner_token = #{owner}
                    AND g.lease_until > #{now}
              )
            """)
    int markDead(
            @Param("xid") long xid,
            @Param("branchId") long branchId,
            @Param("owner") String owner,
            @Param("now") LocalDateTime now);

    /**
     * 插入分支。
     *
     * @param entity 分支实体
     * @return 影响行数
     */
    /**
     * 插入分支。
     *
     * @param entity 分支实体
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO t_tcc_branch
                (xid, branch_id, resource_key, action_name, callback_endpoint,
                 status, retry_count, next_retry_at, application_data, created_at, updated_at)
            VALUES
                (#{xid}, #{branchId}, #{resourceKey}, #{actionName}, #{callbackEndpoint},
                 #{status}, #{retryCount}, #{nextRetryAt}, #{applicationData}, #{createdAt}, #{updatedAt})
            """)
    int insert(TccBranchEntity entity);

    /**
     * 把 TRYING 分支更新为指定的 Try 结果。
     *
     * <p>调用方必须先锁定 global 行并确认 global 仍是 TRYING。
     * 这里只做 branch 状态条件更新，避免在持有 branch 锁时反向锁
     * global，造成与 Commit/Rollback 的锁顺序冲突。</p>
     *
     * @param xid       全局事务号
     * @param branchId  分支号
     * @param status    TRY_SUCCESS 或 TRY_FAIL
     * @param updatedAt 更新时间
     * @return 影响行数；0 表示 branch 不满足条件
     */
    @Update("""
            UPDATE t_tcc_branch
            SET status = #{status}, updated_at = #{updatedAt}
            WHERE xid = #{xid}
              AND branch_id = #{branchId}
              AND status = 'TRYING'
            """)
    int reportTryResult(
            @Param("xid") long xid,
            @Param("branchId") long branchId,
            @Param("status") String status,
            @Param("updatedAt") LocalDateTime updatedAt);
}
