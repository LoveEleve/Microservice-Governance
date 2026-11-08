package com.wuba.crm.tx.tcc.coordinator.persistence;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * {@code t_tcc_branch} 表实体。
 *
 * <p>分支主键是 {@code (xid, branch_id)}，所以没有单列
 * {@code @TableId}；查询和写入使用 Mapper 的显式 SQL。</p>
 */
@TableName("t_tcc_branch")
public class TccBranchEntity {

    /** 全局事务号。 */
    @TableField("xid")
    private Long xid;

    /** 分支号，由 global.branch_seq 分配。 */
    @TableField("branch_id")
    private Long branchId;

    /** 分支幂等键。 */
    @TableField("resource_key")
    private String resourceKey;

    /** TCC 动作名。 */
    @TableField("action_name")
    private String actionName;

    /** RM 回调地址。 */
    @TableField("callback_endpoint")
    private String callbackEndpoint;

    /** 分支状态。 */
    @TableField("status")
    private String status;

    /** Confirm/Cancel 持久化重试次数。 */
    @TableField("retry_count")
    private Integer retryCount;

    /** 下次重试时间。 */
    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;

    /** Confirm/Cancel 上下文快照。 */
    @TableField("application_data")
    private String applicationData;

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

    /** @return 分支号 */
    public Long getBranchId() {
        return branchId;
    }

    /** @param branchId 分支号 */
    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }

    /** @return 分支幂等键 */
    public String getResourceKey() {
        return resourceKey;
    }

    /** @param resourceKey 分支幂等键 */
    public void setResourceKey(String resourceKey) {
        this.resourceKey = resourceKey;
    }

    /** @return TCC 动作名 */
    public String getActionName() {
        return actionName;
    }

    /** @param actionName TCC 动作名 */
    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    /** @return RM 回调地址 */
    public String getCallbackEndpoint() {
        return callbackEndpoint;
    }

    /** @param callbackEndpoint RM 回调地址 */
    public void setCallbackEndpoint(String callbackEndpoint) {
        this.callbackEndpoint = callbackEndpoint;
    }

    /** @return 分支状态 */
    public String getStatus() {
        return status;
    }

    /** @param status 分支状态 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** @return 重试次数 */
    public Integer getRetryCount() {
        return retryCount;
    }

    /** @param retryCount 重试次数 */
    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    /** @return 下次重试时间 */
    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    /** @param nextRetryAt 下次重试时间 */
    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    /** @return 上下文快照 */
    public String getApplicationData() {
        return applicationData;
    }

    /** @param applicationData 上下文快照 */
    public void setApplicationData(String applicationData) {
        this.applicationData = applicationData;
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
