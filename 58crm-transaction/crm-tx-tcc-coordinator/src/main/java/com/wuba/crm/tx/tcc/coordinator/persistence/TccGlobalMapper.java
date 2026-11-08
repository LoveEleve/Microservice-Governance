package com.wuba.crm.tx.tcc.coordinator.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * {@code t_tcc_global} 的 MyBatis Mapper。
 *
 * <p>显式列出列并起 camelCase 别名，避免依赖全局
 * {@code mapUnderscoreToCamelCase} 配置。</p>
 */
@Mapper
public interface TccGlobalMapper {

    /**
     * 按 Begin 幂等键查询。
     *
     * @param requestId 幂等键
     * @return 全局事务；不存在返回 null
     */
    @Select("""
            SELECT xid, request_id AS requestId, biz_code AS bizCode, biz_id AS bizId,
                   status, timeout_at AS timeoutAt, branch_seq AS branchSeq,
                   owner_token AS ownerToken, lease_until AS leaseUntil,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM t_tcc_global
            WHERE request_id = #{requestId}
            """)
    TccGlobalEntity findByRequestId(@Param("requestId") String requestId);

    /**
     * 按 Begin 幂等键加行锁查询。
     *
     * <p>并发 Begin 时，失败方在唯一键冲突后必须用当前读看到胜者，
     * 不能依赖 REPEATABLE READ 下的普通快照读。</p>
     *
     * @param requestId 幂等键
     * @return 全局事务；不存在返回 null
     */
    @Select("""
            SELECT xid, request_id AS requestId, biz_code AS bizCode, biz_id AS bizId,
                   status, timeout_at AS timeoutAt, branch_seq AS branchSeq,
                   owner_token AS ownerToken, lease_until AS leaseUntil,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM t_tcc_global
            WHERE request_id = #{requestId}
            FOR UPDATE
            """)
    TccGlobalEntity findByRequestIdForUpdate(@Param("requestId") String requestId);

    /**
     * 按 XID 加行锁查询，用于 registerBranch 串行分配 branchSeq。
     *
     * @param xid 全局事务号
     * @return 全局事务；不存在返回 null
     */
    @Select("""
            SELECT xid, request_id AS requestId, biz_code AS bizCode, biz_id AS bizId,
                   status, timeout_at AS timeoutAt, branch_seq AS branchSeq,
                   owner_token AS ownerToken, lease_until AS leaseUntil,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM t_tcc_global
            WHERE xid = #{xid}
            FOR UPDATE
            """)
    TccGlobalEntity findByXidForUpdate(@Param("xid") long xid);

    /**
     * 按 XID 查询，不加锁；用于 Query RPC。
     *
     * @param xid 全局事务号
     * @return 全局事务，不存在返回 null
     */
    @Select("""
            SELECT xid, request_id AS requestId, biz_code AS bizCode, biz_id AS bizId,
                   status, timeout_at AS timeoutAt, branch_seq AS branchSeq,
                   owner_token AS ownerToken, lease_until AS leaseUntil,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM t_tcc_global
            WHERE xid = #{xid}
            """)
    TccGlobalEntity findByXid(@Param("xid") long xid);

    /**
     * 抢占 owner 并续租；行锁由调用方事务保证。
     *
     * @param xid       全局事务号
     * @param owner     新 owner token
     * @param leaseUntil 新租约到期时间
     * @param now       当前时间
     * @return 影响行数
     */
    @Update("""
            UPDATE t_tcc_global
            SET owner_token = #{owner}, lease_until = #{leaseUntil}, updated_at = #{now}
            WHERE xid = #{xid}
              AND status IN ('TRYING','CONFIRMING','CANCELLING')
              AND (owner_token IS NULL OR lease_until < #{now})
            """)
    int claimOwner(
            @Param("xid") long xid,
            @Param("owner") String owner,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now);

    /**
     * 续租当前 owner。
     *
     * @param xid        全局事务号
     * @param owner      owner token
     * @param leaseUntil 新租约到期时间
     * @param now        当前时间
     * @return 影响行数
     */
    @Update("""
            UPDATE t_tcc_global
            SET lease_until = #{leaseUntil}, updated_at = #{now}
            WHERE xid = #{xid}
              AND owner_token = #{owner}
              AND lease_until > #{now}
            """)
    int renewOwner(
            @Param("xid") long xid,
            @Param("owner") String owner,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now);

    /**
     * 插入全局事务。
     *
     * @param entity 全局事务实体
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO t_tcc_global
                (xid, request_id, biz_code, biz_id, status, timeout_at, branch_seq,
                 owner_token, lease_until, created_at, updated_at)
            VALUES
                (#{xid}, #{requestId}, #{bizCode}, #{bizId}, #{status}, #{timeoutAt}, #{branchSeq},
                 #{ownerToken}, #{leaseUntil}, #{createdAt}, #{updatedAt})
            """)
    int insert(TccGlobalEntity entity);

    /**
     * 在持有 global 行锁时推进 branch_seq。
     *
     * @param xid       全局事务号
     * @param branchSeq 新的最大分支号
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    @Update("""
            UPDATE t_tcc_global
            SET branch_seq = #{branchSeq}, updated_at = #{updatedAt}
            WHERE xid = #{xid}
              AND status = 'TRYING'
            """)
    int updateBranchSeq(
            @Param("xid") long xid,
            @Param("branchSeq") long branchSeq,
            @Param("updatedAt") java.time.LocalDateTime updatedAt);

    /**
     * 在持有行锁时推进 global 状态并绑定 owner。
     *
     * @param xid          全局事务号
     * @param expected     期望的旧状态
     * @param target       新状态
     * @param owner        owner token
     * @param leaseUntil   租约到期时间
     * @param now          当前时间
     * @return 影响行数
     */
    @Update("""
            UPDATE t_tcc_global
            SET status = #{target}, owner_token = #{owner},
                lease_until = #{leaseUntil}, updated_at = #{now}
            WHERE xid = #{xid}
              AND status = #{expected}
            """)
    int transitionWithOwner(
            @Param("xid") long xid,
            @Param("expected") String expected,
            @Param("target") String target,
            @Param("owner") String owner,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now);

    /**
     * 在持有 owner 时推进 global 终态。
     *
     * @param xid       全局事务号
     * @param expected  期望的旧状态
     * @param target    终态
     * @param owner     owner token
     * @param now       当前时间
     * @return 影响行数
     */
    @Update("""
            UPDATE t_tcc_global
            SET status = #{target}, updated_at = #{now}
            WHERE xid = #{xid}
              AND status = #{expected}
              AND owner_token = #{owner}
              AND lease_until > #{now}
            """)
    int transitionOwned(
            @Param("xid") long xid,
            @Param("expected") String expected,
            @Param("target") String target,
            @Param("owner") String owner,
            @Param("now") LocalDateTime now);
}
