package com.wuba.crm.tx.tcc.example.quota.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * quota 本地业务表 Mapper。
 */
@Mapper
public interface QuotaMapper {

    /**
     * 预占额度。
     *
     * @param userId 用户
     * @return 影响行数
     */
    @Update("""
            UPDATE t_quota
            SET reserved = reserved + 1
            WHERE user_id = #{userId}
            """)
    int reserve(@Param("userId") long userId);

    /**
     * Confirm 正式扣减。
     *
     * @param userId 用户
     * @return 影响行数
     */
    @Update("""
            UPDATE t_quota
            SET reserved = reserved - 1, assigned = assigned + 1
            WHERE user_id = #{userId} AND reserved > 0
            """)
    int confirm(@Param("userId") long userId);

    /**
     * Cancel 释放预占。
     *
     * @param userId 用户
     * @return 影响行数
     */
    @Update("""
            UPDATE t_quota
            SET reserved = reserved - 1
            WHERE user_id = #{userId} AND reserved > 0
            """)
    int cancel(@Param("userId") long userId);
}
