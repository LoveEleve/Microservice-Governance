package com.wuba.crm.tx.tcc.client;

import com.wuba.crm.tx.tcc.client.internal.TccCoordinatorOperations;

import com.wuba.crm.tx.tcc.api.BranchStatus;
import com.wuba.crm.tx.tcc.api.GlobalStatus;
import com.wuba.crm.tx.tcc.protocol.model.TccBeginRequest;
import com.wuba.crm.tx.tcc.protocol.model.TccBeginResult;
import com.wuba.crm.tx.tcc.api.TccBranchSpec;
import com.wuba.crm.tx.tcc.protocol.model.TccBranchRegisterRequest;
import com.wuba.crm.tx.tcc.protocol.model.TccBranchRegisterResult;
import com.wuba.crm.tx.tcc.protocol.model.TccBranchReportRequest;
import com.wuba.crm.tx.tcc.api.TccBranchResult;
import com.wuba.crm.tx.tcc.protocol.model.TccCommitResult;
import com.wuba.crm.tx.tcc.api.TccException;
import com.wuba.crm.tx.tcc.api.TccRequest;
import com.wuba.crm.tx.tcc.api.TccResult;
import com.wuba.crm.tx.tcc.api.TccResultCode;
import com.wuba.crm.tx.tcc.protocol.model.TccRollbackResult;
import com.wuba.crm.tx.tcc.api.TccTransactionTemplate;
import com.wuba.crm.tx.tcc.api.TccTryContext;
import com.wuba.crm.tx.tcc.api.TccTryResult;

import java.util.ArrayList;
import java.util.List;

/**
 * TCC 公共模板的默认实现。
 *
 * <p>负责 begin / register / report / commit / rollback 的固定顺序；
 * 业务方只提供分支描述和 Try 回调。</p>
 */
public class DefaultTccTransactionTemplate implements TccTransactionTemplate {

    private final TccCoordinatorOperations coordinator;

    /**
     * 创建模板。
     *
     * @param coordinator Coordinator 操作
     */
    public DefaultTccTransactionTemplate(TccCoordinatorOperations coordinator) {
        if (coordinator == null) {
            throw new IllegalArgumentException("coordinator must not be null");
        }
        this.coordinator = coordinator;
    }

    /**
     * 执行一次 TCC。
     *
     * @param request 请求
     * @return 执行结果
     */
    @Override
    public TccResult execute(TccRequest request) {
        TccBeginResult begin = coordinator.begin(new TccBeginRequest(
                request.requestId(),
                request.bizCode(),
                request.bizId(),
                timeoutSeconds(request)));
        if (begin.status() != GlobalStatus.TRYING) {
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "begin returned " + begin.status());
        }
        long xid = begin.xid();

        List<RegisteredBranch> registered = new ArrayList<>();
        try {
            for (TccBranchSpec branch : request.branches()) {
                TccBranchRegisterResult result = coordinator.registerBranch(
                        new TccBranchRegisterRequest(
                                xid,
                                branch.resourceKey(),
                                branch.actionName(),
                                branch.callbackEndpoint(),
                                branch.applicationData()));
                if (result.status() != BranchStatus.TRYING) {
                    throw new TccException(TccResultCode.TCC_INVALID_STATE,
                            "register returned " + result.status());
                }
                registered.add(new RegisteredBranch(branch, result.branchId()));
            }
        } catch (RuntimeException failure) {
            rollbackQuietly(xid);
            throw failure;
        }

        List<TccBranchResult> branchResults = new ArrayList<>();
        for (RegisteredBranch entry : registered) {
            TccTryResult tryResult;
            try {
                tryResult = entry.branch.tryAction().execute(new TccTryContext(
                        xid,
                        entry.branchId,
                        entry.branch.resourceKey(),
                        entry.branch.actionName(),
                        entry.branch.applicationData()));
            } catch (RuntimeException failure) {
                try {
                    reportFail(xid, entry.branchId, failure.getMessage());
                } catch (RuntimeException reportFailure) {
                    rollbackQuietly(xid);
                    throw reportFailure;
                }
                GlobalStatus status = rollback(xid);
                branchResults.add(new TccBranchResult(
                        entry.branch.resourceKey(), entry.branchId, BranchStatus.TRY_FAIL));
                return new TccResult(xid, status, branchResults,
                        "try failed: " + failure.getMessage());
            }

            if (tryResult != TccTryResult.SUCCESS) {
                reportFail(xid, entry.branchId, "try returned FAILURE");
                GlobalStatus status = rollback(xid);
                branchResults.add(new TccBranchResult(
                        entry.branch.resourceKey(), entry.branchId, BranchStatus.TRY_FAIL));
                return new TccResult(xid, status, branchResults, "try returned FAILURE");
            }

            try {
                coordinator.reportBranch(new TccBranchReportRequest(
                        xid, entry.branchId, BranchStatus.TRY_SUCCESS, ""));
            } catch (RuntimeException failure) {
                rollbackQuietly(xid);
                throw failure;
            }
            branchResults.add(new TccBranchResult(
                    entry.branch.resourceKey(), entry.branchId, BranchStatus.TRY_SUCCESS));
        }

        try {
            TccCommitResult commit = coordinator.commit(xid);
            return new TccResult(xid, commit.status(), branchResults, "");
        } catch (RuntimeException failure) {
            rollbackQuietly(xid);
            throw failure;
        }
    }

    private void reportFail(long xid, long branchId, String message) {
        coordinator.reportBranch(new TccBranchReportRequest(
                xid, branchId, BranchStatus.TRY_FAIL, message == null ? "" : message));
    }

    private GlobalStatus rollback(long xid) {
        TccRollbackResult result = coordinator.rollback(xid);
        return result.status();
    }

    private void rollbackQuietly(long xid) {
        try {
            coordinator.rollback(xid);
        } catch (RuntimeException ignored) {
            // 保留原始异常，补偿由 TC/Scheduler 继续。
        }
    }

    private int timeoutSeconds(TccRequest request) {
        long seconds = request.tryTimeout().toSeconds();
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, seconds));
    }

    private record RegisteredBranch(TccBranchSpec branch, long branchId) {
    }
}
