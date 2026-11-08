package com.wuba.crm.tx.tcc.client;

import com.wuba.crm.tx.tcc.api.BranchStatus;
import com.wuba.crm.tx.tcc.api.GlobalStatus;
import com.wuba.crm.tx.tcc.protocol.model.TccBranchRegisterRequest;
import com.wuba.crm.tx.tcc.protocol.model.TccBranchRegisterResult;
import com.wuba.crm.tx.tcc.protocol.model.TccBranchReportRequest;
import com.wuba.crm.tx.tcc.protocol.model.TccCommitResult;
import com.wuba.crm.tx.tcc.protocol.model.TccRollbackResult;
import com.wuba.crm.tx.tcc.api.TccBranchResult;
import com.wuba.crm.tx.tcc.api.TccTryResult;
import com.wuba.crm.tx.tcc.client.internal.TccCoordinatorOperations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 注解事务的活动上下文。
 *
 * <p>一个 TccAnnotationTransaction 对应一个 xid；分支通过 register 加入。</p>
 */
public class TccAnnotationTransaction {

    private final long xid;
    private final TccCoordinatorOperations coordinator;
    private final List<TccBranchResult> branches = new ArrayList<>();
    private final Map<Long, Integer> branchIndexes = new HashMap<>();
    private final Set<String> resourceKeys = new HashSet<>();

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
        if (!resourceKeys.add(resourceKey)) {
            throw new IllegalArgumentException(
                    "duplicate resourceKey in one TCC transaction: " + resourceKey);
        }
        TccBranchRegisterResult result = coordinator.registerBranch(
                new TccBranchRegisterRequest(
                        xid, resourceKey, actionName, callbackEndpoint, applicationData));
        if (result.status() != BranchStatus.TRYING) {
            throw new IllegalStateException("register returned " + result.status());
        }
        branches.add(new TccBranchResult(resourceKey, result.branchId(), BranchStatus.TRYING));
        branchIndexes.put(result.branchId(), branches.size() - 1);
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
        Integer index = branchIndexes.get(branchId);
        if (index != null) {
            TccBranchResult current = branches.get(index);
            branches.set(index, new TccBranchResult(
                    current.resourceKey(), branchId, status));
        }
    }

    /**
     * 当前事务已注册分支的快照。
     *
     * @return 分支结果
     */
    public List<TccBranchResult> branches() {
        return List.copyOf(branches);
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
