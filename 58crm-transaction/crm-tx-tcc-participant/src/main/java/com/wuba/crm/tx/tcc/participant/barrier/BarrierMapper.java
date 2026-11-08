package com.wuba.crm.tx.tcc.participant.barrier;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * {@code t_branch_barrier} 的 MyBatis Mapper。
 *
 * <p>barrier 主键是 {@code (xid, branch_id)}；所有状态变化都在本地
 * 事务中完成。</p>
 */
@Mapper
public interface BarrierMapper {

    /**
     * 加行锁读取 barrier。
     *
     * @param xid      全局事务号
     * @param branchId 分支号
     * @return barrier，不存在返回 null
     */
    @Select("""
            SELECT xid, branch_id AS branchId, action_name AS actionName,
                   status, created_at AS createdAt, updated_at AS updatedAt
            FROM t_branch_barrier
            WHERE xid = #{xid} AND branch_id = #{branchId}
            FOR UPDATE
            """)
    BarrierEntity findForUpdate(
            @Param("xid") long xid,
            @Param("branchId") long branchId);

    /**
     * 插入 barrier。
     *
     * @param entity barrier 实体
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO t_branch_barrier
                (xid, branch_id, action_name, status, created_at, updated_at)
            VALUES
                (#{xid}, #{branchId}, #{actionName}, #{status}, #{createdAt}, #{updatedAt})
            """)
    int insert(BarrierEntity entity);

    /**
     * TRIED -> CONFIRMED。
     *
     * @param xid       全局事务号
     * @param branchId  分支号
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    @Update("""
            UPDATE t_branch_barrier
            SET status = 'CONFIRMED', updated_at = #{updatedAt}
            WHERE xid = #{xid}
              AND branch_id = #{branchId}
              AND status = 'TRIED'
            """)
    int markConfirmed(
            @Param("xid") long xid,
            @Param("branchId") long branchId,
            @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * TRIED -> CANCELLED。
     *
     * @param xid       全局事务号
     * @param branchId  分支号
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    @Update("""
            UPDATE t_branch_barrier
            SET status = 'CANCELLED', updated_at = #{updatedAt}
            WHERE xid = #{xid}
              AND branch_id = #{branchId}
              AND status = 'TRIED'
            """)
    int markCancelled(
            @Param("xid") long xid,
            @Param("branchId") long branchId,
            @Param("updatedAt") LocalDateTime updatedAt);
}
