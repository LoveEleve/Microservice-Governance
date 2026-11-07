package com.wuba.crm.tx.outbox.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 本地消息表的 MyBatis-Plus Mapper。
 *
 * <p>后续 Relay 和 Consumer 会在这个 Mapper 上增加条件更新方法；
 * C-001.1 只要求能插入 INIT 消息，因此先保持最小接口。</p>
 */
@Mapper
public interface LocalMessageMapper extends BaseMapper<LocalMessageEntity> {

    @Insert("""
            INSERT IGNORE INTO t_local_message
                (msg_id, event_key, biz_code, biz_id, event_type, payload, payload_ref,
                 status, retry_count, next_retry_at, stream_entry_id, created_at, updated_at)
            VALUES
                (#{msgId}, #{eventKey}, #{bizCode}, #{bizId}, #{eventType}, #{payload}, #{payloadRef},
                 #{status}, #{retryCount}, #{nextRetryAt}, #{streamEntryId}, #{createdAt}, #{updatedAt})
            """)
    int insertIgnore(LocalMessageEntity entity);

    @Select("SELECT * FROM t_local_message WHERE event_key = #{eventKey} LIMIT 1")
    LocalMessageEntity selectByEventKey(@Param("eventKey") String eventKey);
}
