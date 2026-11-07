package com.wuba.crm.tx.consumer.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface IdempotentMapper extends BaseMapper<IdempotentRecordEntity> {

    @Insert("""
            INSERT IGNORE INTO t_idempotent (msg_id, biz_key, status, created_at)
            VALUES (#{msgId}, #{bizKey}, #{status}, #{createdAt})
            """)
    int insertIgnore(
            @Param("msgId") String msgId,
            @Param("bizKey") String bizKey,
            @Param("status") String status,
            @Param("createdAt") LocalDateTime createdAt);
}
