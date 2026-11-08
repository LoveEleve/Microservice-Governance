package com.wuba.crm.tx.tcc.client;

import com.wuba.crm.tx.tcc.api.BranchStatus;
import com.wuba.crm.tx.tcc.api.GlobalStatus;
import com.wuba.crm.tx.tcc.protocol.model.TccBranchRegisterRequest;
import com.wuba.crm.tx.tcc.protocol.model.TccBranchRegisterResult;
import com.wuba.crm.tx.tcc.protocol.model.TccBranchReportRequest;
import com.wuba.crm.tx.tcc.protocol.model.TccCommitResult;
import com.wuba.crm.tx.tcc.protocol.model.TccRollbackResult;
import com.wuba.crm.tx.tcc.api.TccTryResult;
import com.wuba.crm.tx.tcc.client.internal.TccCoordinatorOperations;

/**
 * 注解事务的活动上下文。
 *
 * <p>一个 TccAnnotationTransaction 对应一个 xid；分支通过 register 加入。</p>
 */
public class TccAnnotationTransaction {

    private final long xid;
    private final TccCoordinatorOperations coordinator;

    /**
     * 创建活动事务。
     *
     * @param xid         全局事务号
     * @param coordinator Coordinator 操作
     */
    public TccAnnotationTransaction(long xid, TccCoordinatorOperations coordinator) {
        this.xid = xid;
        this.coordinator = coordinator;
    }

    /** @return 全局事务号 */
    public long xid() {
        return xid;
    }

    /**
     * 注册分支。
     *
     * @param resourceKey      分支幂等键
     * @param actionName       动作名
     * @param callbackEndpoint callback endpoint
     * @param applicationData  业务上下文
     * @return branchId
     */
    public long registerBranch(
            String resourceKey,
            String actionName,
            String callbackEndpoint,
            String applicationData) {
        TccBranchRegisterResult result = coordinator.registerBranch(
                new TccBranchRegisterRequest(
                        xid, resourceKey, actionName, callbackEndpoint, applicationData));
        if (result.status() != BranchStatus.TRYING) {
            throw new IllegalStateException("register returned " + result.status());
        }
        return result.branchId();
    }

    /**
     * 上报 Try 结果。
     *
     * @param branchId 分支号
     * @param result   Try 结果
     */
    public void report(long branchId, TccTryResult result) {
        BranchStatus status = result == TccTryResult.SUCCESS
                ? BranchStatus.TRY_SUCCESS : BranchStatus.TRY_FAIL;
        coordinator.reportBranch(new TccBranchReportRequest(
                xid, branchId, status, ""));
    }

    /**
     * 提交。
     *
     * @return 状态
     */
    public GlobalStatus commit() {
        TccCommitResult result = coordinator.commit(xid);
        return result.status();
    }

    /**
     * 回滚。
     *
     * @return 状态
     */
    public GlobalStatus rollback() {
        TccRollbackResult result = coordinator.rollback(xid);
        return result.status();
    }
}
