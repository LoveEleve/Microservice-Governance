package com.wuba.crm.tx.tcc.participant.barrier;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * {@code t_branch_barrier} 实体。
 *
 * <p>barrier 在 RM 本地库中，和业务预占/释放使用同一个事务。</p>
 */
@TableName("t_branch_barrier")
public class BarrierEntity {

    /** 全局事务号。 */
    @TableField("xid")
    private Long xid;

    /** 分支号。 */
    @TableField("branch_id")
    private Long branchId;

    /** TCC 动作名。 */
    @TableField("action_name")
    private String actionName;

    /** TRIED / CONFIRMED / CANCELLED / SUSPENDED。 */
    @TableField("status")
    private String status;

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

    /** @return TCC 动作名 */
    public String getActionName() {
        return actionName;
    }

    /** @param actionName TCC 动作名 */
    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    /** @return barrier 状态 */
    public String getStatus() {
        return status;
    }

    /** @param status barrier 状态 */
    public void setStatus(String status) {
        this.status = status;
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
