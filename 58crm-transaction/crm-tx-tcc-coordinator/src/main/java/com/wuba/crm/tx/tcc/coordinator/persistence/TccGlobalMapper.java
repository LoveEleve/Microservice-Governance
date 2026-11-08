package com.wuba.crm.tx.tcc.coordinator.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
}
