package com.wuba.crm.tx.tcc.coordinator.service;

import com.wuba.crm.tx.tcc.api.BranchStatus;
import com.wuba.crm.tx.tcc.api.GlobalStatus;
import com.wuba.crm.tx.tcc.api.TccBeginRequest;
import com.wuba.crm.tx.tcc.api.TccBeginResult;
import com.wuba.crm.tx.tcc.api.TccBranchRegisterRequest;
import com.wuba.crm.tx.tcc.api.TccBranchRegisterResult;
import com.wuba.crm.tx.tcc.api.TccBranchReportRequest;
import com.wuba.crm.tx.tcc.api.TccException;
import com.wuba.crm.tx.tcc.api.TccResultCode;
import com.wuba.crm.tx.tcc.coordinator.persistence.TccBranchEntity;
import com.wuba.crm.tx.tcc.coordinator.persistence.TccBranchRepository;
import com.wuba.crm.tx.tcc.coordinator.persistence.TccGlobalEntity;
import com.wuba.crm.tx.tcc.coordinator.persistence.TccGlobalRepository;
import com.wuba.crm.tx.tcc.id.TccXidGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * TCC Coordinator 的第一阶段服务。
 *
 * <p>负责 Begin、RegisterBranch、ReportBranch。Commit/Rollback 和
 * TC -> RM callback 属于 C-003.3，不在本类实现。</p>
 *
 * <p>所有写方法都必须在 Spring 事务内执行；Begin 的 requestId 唯一键
 * 负责跨实例幂等，RegisterBranch 的 global 行锁负责 branch_seq 串行。</p>
 */
@Service
public class TccCoordinatorService {

    private final TccGlobalRepository globals;
    private final TccBranchRepository branches;
    private final TccXidGenerator xidGenerator;
    private final Clock clock;

    /**
     * 创建服务。
     *
     * @param globals       全局事务仓储
     * @param branches      分支事务仓储
     * @param xidGenerator  XID 生成器
     * @param clock         UTC 时钟
     */
    public TccCoordinatorService(
            TccGlobalRepository globals,
            TccBranchRepository branches,
            TccXidGenerator xidGenerator,
            Clock clock) {
        if (globals == null || branches == null || xidGenerator == null || clock == null) {
            throw new IllegalArgumentException("coordinator dependencies must not be null");
        }
        this.globals = globals;
        this.branches = branches;
        this.xidGenerator = xidGenerator;
        this.clock = clock;
    }

    /**
     * 开启全局事务。
     *
     * <p>同一个 requestId 重复调用返回已有 XID；如果业务标识不同，
     * 说明调用方复用了幂等键，直接拒绝。</p>
     *
     * @param request Begin 请求
     * @return 全局事务结果
     */
    @Transactional
    public TccBeginResult begin(TccBeginRequest request) {
        TccGlobalEntity existing = globals.findByRequestId(request.requestId());
        if (existing != null) {
            assertSameRequest(existing, request);
            return toBeginResult(existing);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        TccGlobalEntity entity = new TccGlobalEntity();
        entity.setXid(xidGenerator.nextXid());
        entity.setRequestId(request.requestId());
        entity.setBizCode(request.bizCode());
        entity.setBizId(request.bizId());
        entity.setStatus(GlobalStatus.TRYING.name());
        entity.setTimeoutAt(now.plusSeconds(request.tryTimeoutSeconds()));
        entity.setBranchSeq(0L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        try {
            globals.insert(entity);
        } catch (DuplicateKeyException duplicate) {
            // 并发 Begin：唯一键已经由另一个实例写入，读取并返回同一 XID。
            TccGlobalEntity winner = globals.findByRequestIdForUpdate(request.requestId());
            if (winner == null) {
                throw duplicate;
            }
            assertSameRequest(winner, request);
            return toBeginResult(winner);
        }
        return toBeginResult(entity);
    }

    /**
     * 注册分支。
     *
     * <p>global 行锁保证同一 XID 的 branch_seq 不会并发分配出重复值。
     * 同一 resourceKey 重试返回已有分支，但 actionName、callbackEndpoint
     * 或 applicationData 必须一致。</p>
     *
     * @param request 分支注册请求
     * @return 分支结果
     */
    @Transactional
    public TccBranchRegisterResult registerBranch(TccBranchRegisterRequest request) {
        TccGlobalEntity global = globals.findByXidForUpdate(request.xid());
        if (global == null) {
            throw new TccException(TccResultCode.TCC_NOT_FOUND, "global tcc not found");
        }
        if (parseGlobalStatus(global.getStatus()) != GlobalStatus.TRYING) {
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "global is not TRYING: " + global.getStatus());
        }

        TccBranchEntity existing = branches.findByResourceKey(
                request.xid(), request.resourceKey());
        if (existing != null) {
            assertSameBranch(existing, request);
            return toBranchResult(existing);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (!global.getTimeoutAt().isAfter(now)) {
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "global try timeout has expired");
        }

        long branchId = global.getBranchSeq() + 1;
        int updated = globals.updateBranchSeq(request.xid(), branchId, now);
        if (updated != 1) {
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "global branch sequence update failed");
        }

        TccBranchEntity branch = new TccBranchEntity();
        branch.setXid(request.xid());
        branch.setBranchId(branchId);
        branch.setResourceKey(request.resourceKey());
        branch.setActionName(request.actionName());
        branch.setCallbackEndpoint(request.callbackEndpoint());
        branch.setStatus(BranchStatus.TRYING.name());
        branch.setRetryCount(0);
        branch.setApplicationData(request.applicationData());
        branch.setCreatedAt(now);
        branch.setUpdatedAt(now);
        branches.insert(branch);

        return new TccBranchRegisterResult(branchId, BranchStatus.TRYING);
    }

    /**
     * 上报 Try 结果。
     *
     * <p>只有 global TRYING 且 branch TRYING 时才落状态；重复上报同一
     * 状态幂等，冲突状态返回 {@code TCC_INVALID_STATE}。</p>
     *
     * @param request 上报请求
     */
    @Transactional
    public void reportBranch(TccBranchReportRequest request) {
        // 与 Commit/Rollback 保持“先 global、后 branch”的锁顺序，避免死锁。
        TccGlobalEntity global = globals.findByXidForUpdate(request.xid());
        if (global == null) {
            throw new TccException(TccResultCode.TCC_NOT_FOUND, "global tcc not found");
        }

        String requestedStatus = request.status().name();
        TccBranchEntity branch = branches.findByBranchId(request.xid(), request.branchId());
        if (branch == null) {
            throw new TccException(TccResultCode.TCC_NOT_FOUND, "branch not found");
        }

        if (parseGlobalStatus(global.getStatus()) != GlobalStatus.TRYING) {
            if (Objects.equals(branch.getStatus(), requestedStatus)) {
                return;
            }
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "global is not TRYING: " + global.getStatus());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int updated = branches.reportTryResult(
                request.xid(), request.branchId(), requestedStatus, now);
        if (updated == 1) {
            return;
        }

        if (Objects.equals(branch.getStatus(), requestedStatus)) {
            return;
        }
        throw new TccException(TccResultCode.TCC_INVALID_STATE,
                "branch cannot report " + requestedStatus
                        + " from " + branch.getStatus());
    }

    private TccBeginResult toBeginResult(TccGlobalEntity entity) {
        return new TccBeginResult(entity.getXid(), parseGlobalStatus(entity.getStatus()));
    }

    private TccBranchRegisterResult toBranchResult(TccBranchEntity entity) {
        return new TccBranchRegisterResult(
                entity.getBranchId(), parseBranchStatus(entity.getStatus()));
    }

    private void assertSameRequest(TccGlobalEntity existing, TccBeginRequest request) {
        if (!Objects.equals(existing.getBizCode(), request.bizCode())
                || !Objects.equals(existing.getBizId(), request.bizId())) {
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "requestId reused with different biz identity");
        }
    }

    private void assertSameBranch(TccBranchEntity existing, TccBranchRegisterRequest request) {
        if (!Objects.equals(existing.getActionName(), request.actionName())
                || !Objects.equals(existing.getCallbackEndpoint(), request.callbackEndpoint())
                || !Objects.equals(existing.getApplicationData(), request.applicationData())) {
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "resourceKey reused with different branch definition");
        }
    }

    private GlobalStatus parseGlobalStatus(String status) {
        try {
            return GlobalStatus.valueOf(status);
        } catch (IllegalArgumentException invalid) {
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "unknown global status: " + status, invalid);
        }
    }

    private BranchStatus parseBranchStatus(String status) {
        try {
            return BranchStatus.valueOf(status);
        } catch (IllegalArgumentException invalid) {
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "unknown branch status: " + status, invalid);
        }
    }
}
