package com.wuba.crm.tx.consumer.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ConsumerRecordMapper extends BaseMapper<ConsumerRecordEntity> {

    @Insert("""
            <script>
            INSERT IGNORE INTO t_consume_record
                (msg_id, biz_code, biz_id, event_type, stream_entry_id,
                 consumer_id, status, owner_token, owner_epoch, claim_count,
                 lease_until, retry_count, next_retry_at, payload, payload_ref,
                 created_at, updated_at)
            VALUES
            <foreach collection="records" item="r" separator=",">
                (#{r.msgId}, #{r.bizCode}, #{r.bizId}, #{r.eventType}, #{r.streamEntryId},
                 #{r.consumerId}, #{r.status}, #{r.ownerToken}, #{r.ownerEpoch}, #{r.claimCount},
                 #{r.leaseUntil}, #{r.retryCount}, #{r.nextRetryAt}, #{r.payload}, #{r.payloadRef},
                 #{r.createdAt}, #{r.updatedAt})
            </foreach>
            </script>
            """)
    int insertReadyBatch(@Param("records") List<ConsumerRecordEntity> records);

    @Select("""
            SELECT msg_id
            FROM t_consume_record
            WHERE biz_code = #{bizCode}
              AND status = 'READY'
              AND (next_retry_at IS NULL OR next_retry_at <= #{now})
            ORDER BY created_at
            LIMIT #{limit}
            """)
    List<String> selectReadyIds(
            @Param("bizCode") String bizCode,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    @Update("""
            UPDATE t_consume_record
            SET status = 'PROCESSING',
                consumer_id = #{consumerId},
                owner_token = #{ownerToken},
                owner_epoch = owner_epoch + 1,
                claim_count = claim_count + 1,
                lease_until = #{leaseUntil},
                updated_at = #{now}
            WHERE msg_id = #{msgId}
              AND status = 'READY'
            """)
    int claimReadyById(
            @Param("msgId") String msgId,
            @Param("consumerId") String consumerId,
            @Param("ownerToken") String ownerToken,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    @Select("""
            SELECT *
            FROM t_consume_record
            WHERE owner_token = #{ownerToken}
              AND status = 'PROCESSING'
            ORDER BY created_at
            """)
    List<ConsumerRecordEntity> findByOwnerToken(@Param("ownerToken") String ownerToken);

    @Select("""
            SELECT *
            FROM t_consume_record
            WHERE stream_entry_id = #{streamEntryId}
            LIMIT 1
            """)
    ConsumerRecordEntity findByStreamEntryId(@Param("streamEntryId") String streamEntryId);

    @Update("""
            UPDATE t_consume_record
            SET status = 'READY',
                consumer_id = NULL,
                owner_token = NULL,
                lease_until = NULL,
                updated_at = #{now}
            WHERE msg_id = #{msgId}
              AND status = 'PROCESSING'
              AND lease_until <= #{now}
            """)
    int markExpiredProcessingReady(
            @Param("msgId") String msgId,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE t_consume_record
            SET lease_until = #{leaseUntil},
                updated_at = #{now}
            WHERE msg_id = #{msgId}
              AND status = 'PROCESSING'
              AND owner_token = #{ownerToken}
              AND owner_epoch = #{ownerEpoch}
              AND lease_until > #{now}
            """)
    int renewLease(
            @Param("msgId") String msgId,
            @Param("ownerToken") String ownerToken,
            @Param("ownerEpoch") long ownerEpoch,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now);

    @Update("""
            <script>
            UPDATE t_consume_record
            SET lease_until = #{leaseUntil},
                updated_at = #{now}
            WHERE msg_id IN
            <foreach collection="msgIds" item="msgId" open="(" separator="," close=")">
                #{msgId}
            </foreach>
              AND status = 'PROCESSING'
              AND owner_token = #{ownerToken}
              AND owner_epoch = #{ownerEpoch}
              AND lease_until > #{now}
            </script>
            """)
    int renewLeaseBatch(
            @Param("msgIds") List<String> msgIds,
            @Param("ownerToken") String ownerToken,
            @Param("ownerEpoch") long ownerEpoch,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE t_consume_record
            SET status = 'CONSUMED',
                lease_until = NULL,
                updated_at = #{now}
            WHERE msg_id = #{msgId}
              AND status = 'PROCESSING'
              AND owner_token = #{ownerToken}
              AND owner_epoch = #{ownerEpoch}
              AND lease_until > #{now}
            """)
    int markConsumed(
            @Param("msgId") String msgId,
            @Param("ownerToken") String ownerToken,
            @Param("ownerEpoch") long ownerEpoch,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE t_consume_record
            SET status = CASE
                    WHEN retry_count + 1 >= #{maxRetries} THEN 'DEAD'
                    ELSE 'READY'
                END,
                next_retry_at = CASE
                    WHEN retry_count + 1 >= #{maxRetries} THEN NULL
                    ELSE #{nextRetryAt}
                END,
                retry_count = retry_count + 1,
                lease_until = NULL,
                consumer_id = NULL,
                owner_token = NULL,
                updated_at = #{now}
            WHERE msg_id = #{msgId}
              AND status = 'PROCESSING'
              AND owner_token = #{ownerToken}
              AND owner_epoch = #{ownerEpoch}
              AND lease_until > #{now}
            """)
    int markRetry(
            @Param("msgId") String msgId,
            @Param("ownerToken") String ownerToken,
            @Param("ownerEpoch") long ownerEpoch,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("maxRetries") int maxRetries,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE t_consume_record
            SET status = 'DEAD',
                lease_until = NULL,
                consumer_id = NULL,
                owner_token = NULL,
                updated_at = #{now}
            WHERE msg_id = #{msgId}
              AND status = 'PROCESSING'
              AND owner_token = #{ownerToken}
              AND owner_epoch = #{ownerEpoch}
            """)
    int markDead(
            @Param("msgId") String msgId,
            @Param("ownerToken") String ownerToken,
            @Param("ownerEpoch") long ownerEpoch,
            @Param("now") LocalDateTime now);

    @Select("""
            SELECT *
            FROM t_consume_record
            WHERE status = 'PROCESSING'
              AND lease_until <= #{now}
            ORDER BY lease_until
            LIMIT #{limit}
            """)
    List<ConsumerRecordEntity> findExpiredLeases(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);
}
