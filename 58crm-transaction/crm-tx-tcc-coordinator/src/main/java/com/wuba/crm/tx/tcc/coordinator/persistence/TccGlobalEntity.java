package com.wuba.crm.tx.tcc.coordinator.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * {@code t_tcc_global} 表实体。
 *
 * <p>一个 XID 对应一行，记录全局状态、Try 超时、分支序号和 owner 租约。</p>
 */
@TableName("t_tcc_global")
public class TccGlobalEntity {

    /** 全局事务号，由号段生成器产生。 */
    @TableId(value = "xid", type = IdType.INPUT)
    private Long xid;

    /** Begin 幂等键。 */
    @TableField("request_id")
    private String requestId;

    /** 业务域。 */
    @TableField("biz_code")
    private String bizCode;

    /** 业务单号。 */
    @TableField("biz_id")
    private String bizId;

    /** 全局状态。 */
    @TableField("status")
    private String status;

    /** Try 阶段超时点。 */
    @TableField("timeout_at")
    private LocalDateTime timeoutAt;

    /** 已分配的最大分支号。 */
    @TableField("branch_seq")
    private Long branchSeq;

    /** 当前推进 owner。 */
    @TableField("owner_token")
    private String ownerToken;

    /** owner 租约到期时间。 */
    @TableField("lease_until")
    private LocalDateTime leaseUntil;

    /** 创建时间，UTC。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间，UTC。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** @return 全局事务号 */
    public Long getXid() {
        return xid;
    }

    /** @param xid 全局事务号 */
    public void setXid(Long xid) {
        this.xid = xid;
    }

    /** @return Begin 幂等键 */
    public String getRequestId() {
        return requestId;
    }

    /** @param requestId Begin 幂等键 */
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /** @return 业务域 */
    public String getBizCode() {
        return bizCode;
    }

    /** @param bizCode 业务域 */
    public void setBizCode(String bizCode) {
        this.bizCode = bizCode;
    }

    /** @return 业务单号 */
    public String getBizId() {
        return bizId;
    }

    /** @param bizId 业务单号 */
    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    /** @return 全局状态 */
    public String getStatus() {
        return status;
    }

    /** @param status 全局状态 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** @return Try 超时点 */
    public LocalDateTime getTimeoutAt() {
        return timeoutAt;
    }

    /** @param timeoutAt Try 超时点 */
    public void setTimeoutAt(LocalDateTime timeoutAt) {
        this.timeoutAt = timeoutAt;
    }

    /** @return 最大分支号 */
    public Long getBranchSeq() {
        return branchSeq;
    }

    /** @param branchSeq 最大分支号 */
    public void setBranchSeq(Long branchSeq) {
        this.branchSeq = branchSeq;
    }

    /** @return owner token */
    public String getOwnerToken() {
        return ownerToken;
    }

    /** @param ownerToken owner token */
    public void setOwnerToken(String ownerToken) {
        this.ownerToken = ownerToken;
    }

    /** @return 租约到期时间 */
    public LocalDateTime getLeaseUntil() {
        return leaseUntil;
    }

    /** @param leaseUntil 租约到期时间 */
    public void setLeaseUntil(LocalDateTime leaseUntil) {
        this.leaseUntil = leaseUntil;
    }

    /** @return 创建时间 */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** @param createdAt 创建时间 */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** @return 更新时间 */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** @param updatedAt 更新时间 */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
