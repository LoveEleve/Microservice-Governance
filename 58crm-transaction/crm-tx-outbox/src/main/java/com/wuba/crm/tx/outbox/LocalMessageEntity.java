package com.wuba.crm.tx.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * {@code t_local_message} 的 MyBatis-Plus 映射实体。
 *
 * <p>消息正文和状态必须和业务数据在同一个 MySQL 本地事务中提交。
 * Redis 只保存指向这条记录的 {@code bizId + msgId} 信封，不保存正本。</p>
 */
@TableName("t_local_message")
public class LocalMessageEntity {

    @TableId(value = "msg_id", type = IdType.INPUT)
    private String msgId;

    @TableField("event_key")
    private String eventKey;

    @TableField("biz_code")
    private String bizCode;

    @TableField("biz_id")
    private String bizId;

    @TableField("event_type")
    private String eventType;

    /** 消息正文；小消息直接落库，大消息使用 {@link #payloadRef}。 */
    @TableField("payload")
    private String payload;

    /** 大消息在持久对象存储中的引用，与 payload 至少有一个非空。 */
    @TableField("payload_ref")
    private String payloadRef;

    /** 取值见 {@code MessageStatus}，数据库存字符串，便于排障和 SQL 查询。 */
    @TableField("status")
    private String status;

    @TableField("lease_until")
    private LocalDateTime leaseUntil;

    @TableField("consumer_id")
    private String consumerId;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    public String getBizCode() {
        return bizCode;
    }

    public void setBizCode(String bizCode) {
        this.bizCode = bizCode;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getPayloadRef() {
        return payloadRef;
    }

    public void setPayloadRef(String payloadRef) {
        this.payloadRef = payloadRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLeaseUntil() {
        return leaseUntil;
    }

    public void setLeaseUntil(LocalDateTime leaseUntil) {
        this.leaseUntil = leaseUntil;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
