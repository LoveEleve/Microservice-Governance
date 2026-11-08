package com.wuba.crm.tx.tcc.coordinator.service;

import com.wuba.crm.tx.tcc.api.BranchStatus;
import com.wuba.crm.tx.tcc.api.GlobalStatus;
import com.wuba.crm.tx.tcc.api.TccBranchInfo;
import com.wuba.crm.tx.tcc.api.TccCallbackResult;
import com.wuba.crm.tx.tcc.api.TccCommitResult;
import com.wuba.crm.tx.tcc.api.TccException;
import com.wuba.crm.tx.tcc.api.TccPhaseResult;
import com.wuba.crm.tx.tcc.api.TccQueryResult;
import com.wuba.crm.tx.tcc.api.TccResultCode;
import com.wuba.crm.tx.tcc.api.TccRollbackResult;
import com.wuba.crm.tx.tcc.coordinator.callback.TccParticipantClient;
import com.wuba.crm.tx.tcc.coordinator.persistence.TccBranchEntity;
import com.wuba.crm.tx.tcc.coordinator.persistence.TccBranchRepository;
import com.wuba.crm.tx.tcc.coordinator.persistence.TccGlobalEntity;
import com.wuba.crm.tx.tcc.coordinator.persistence.TccGlobalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 二阶段协调器：Commit / Rollback / Query。
 *
 * <p>数据库事务只覆盖状态推进和分支预推进；RM 回调在数据库事务外执行，
 * 避免网络 I/O 持有 MySQL 行锁。</p>
 */
@Service
public class TccPhase2Coordinator {

    private static final long LEASE_SECONDS = 30;

    private final TccGlobalRepository globals;
    private final TccBranchRepository branches;
    private final TccParticipantClient participantClient;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * 创建二阶段协调器。
     *
     * @param globals           全局事务仓储
     * @param branches          分支仓储
     * @param participantClient RM 回调客户端
     * @param transactionManager 事务管理器
     * @param clock             UTC 时钟
     */
    public TccPhase2Coordinator(
            TccGlobalRepository globals,
            TccBranchRepository branches,
            TccParticipantClient participantClient,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        if (globals == null || branches == null || participantClient == null
                || transactionManager == null || clock == null) {
            throw new IllegalArgumentException("phase2 dependencies must not be null");
        }
        this.globals = globals;
        this.branches = branches;
        this.participantClient = participantClient;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 提交全局事务。
     *
     * @param xid 全局事务号
     * @return 当前全局状态
     */
    public TccCommitResult commit(long xid) {
        Prepared prepared = transactionTemplate.execute(status -> prepareCommit(xid));
        if (prepared == null) {
            throw new TccException(TccResultCode.TCC_INTERNAL_ERROR, "commit preparation missing");
        }
        if (prepared.terminal) {
            return new TccCommitResult(prepared.status);
        }
        return executeConfirm(prepared);
    }

    /**
     * 回滚全局事务。
     *
     * @param xid 全局事务号
     * @return 当前全局状态
     */
    public TccRollbackResult rollback(long xid) {
        Prepared prepared = transactionTemplate.execute(status -> prepareRollback(xid));
        if (prepared == null) {
            throw new TccException(TccResultCode.TCC_INTERNAL_ERROR, "rollback preparation missing");
        }
        if (prepared.terminal) {
            return new TccRollbackResult(prepared.status);
        }
        return executeCancel(prepared);
    }

    /**
     * 查询全局事务和全部分支。
     *
     * @param xid 全局事务号
     * @return 查询结果
     */
    public TccQueryResult query(long xid) {
        TccGlobalEntity global = globals.findByXid(xid);
        if (global == null) {
            throw new TccException(TccResultCode.TCC_NOT_FOUND, "global tcc not found");
        }
        List<TccBranchInfo> branchInfos = branches.findByXid(xid).stream()
                .map(this::toBranchInfo)
                .toList();
        return new TccQueryResult(parseGlobal(global.getStatus()), branchInfos);
    }

    private Prepared prepareCommit(long xid) {
        LocalDateTime now = LocalDateTime.now(clock);
        TccGlobalEntity global = globals.findByXidForUpdate(xid);
        if (global == null) {
            throw new TccException(TccResultCode.TCC_NOT_FOUND, "global tcc not found");
        }
        GlobalStatus status = parseGlobal(global.getStatus());

        if (status == GlobalStatus.CONFIRMED) {
            return new Prepared(xid, status, null, List.of(), true);
        }
        if (status == GlobalStatus.CANCELLING
                || status == GlobalStatus.CANCELLED
                || status == GlobalStatus.FAILED) {
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "cannot commit from " + status);
        }

        if (hasActiveOwner(global, now)) {
            throw new TccException(TccResultCode.TCC_BUSY, "global is owned by another TC");
        }

        String owner = UUID.randomUUID().toString();
        LocalDateTime leaseUntil = now.plusSeconds(LEASE_SECONDS);

        if (status == GlobalStatus.TRYING) {
            List<TccBranchEntity> current = branches.findByXid(xid);
            if (current.isEmpty()) {
                throw new TccException(TccResultCode.TCC_NOT_READY, "no branches");
            }
            for (TccBranchEntity branch : current) {
                BranchStatus branchStatus = parseBranch(branch.getStatus());
                if (branchStatus == BranchStatus.TRYING) {
                    throw new TccException(TccResultCode.TCC_NOT_READY,
                            "branch still TRYING: " + branch.getBranchId());
                }
                if (branchStatus == BranchStatus.TRY_FAIL) {
                    throw new TccException(TccResultCode.TCC_INVALID_STATE,
                            "branch Try failed: " + branch.getBranchId());
                }
            }
            int transitioned = globals.transitionWithOwner(
                    xid, GlobalStatus.TRYING.name(), GlobalStatus.CONFIRMING.name(),
                    owner, leaseUntil, now);
            if (transitioned != 1) {
                throw new TccException(TccResultCode.TCC_BUSY, "commit state changed");
            }
            branches.updateToConfirming(xid, now, now);
            return new Prepared(xid, GlobalStatus.CONFIRMING, owner, branches.findByXid(xid), false);
        }

        // 已经是 CONFIRMING：只允许过期 owner 接管，不重复预推进分支。
        int claimed = globals.claimOwner(xid, owner, leaseUntil, now);
        if (claimed != 1) {
            throw new TccException(TccResultCode.TCC_BUSY, "cannot claim global owner");
        }
        return new Prepared(xid, GlobalStatus.CONFIRMING, owner, branches.findByXid(xid), false);
    }

    private Prepared prepareRollback(long xid) {
        LocalDateTime now = LocalDateTime.now(clock);
        TccGlobalEntity global = globals.findByXidForUpdate(xid);
        if (global == null) {
            throw new TccException(TccResultCode.TCC_NOT_FOUND, "global tcc not found");
        }
        GlobalStatus status = parseGlobal(global.getStatus());

        if (status == GlobalStatus.CANCELLED) {
            return new Prepared(xid, status, null, List.of(), true);
        }
        if (status == GlobalStatus.CONFIRMING
                || status == GlobalStatus.CONFIRMED
                || status == GlobalStatus.FAILED) {
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "cannot rollback from " + status);
        }

        if (hasActiveOwner(global, now)) {
            throw new TccException(TccResultCode.TCC_BUSY, "global is owned by another TC");
        }

        String owner = UUID.randomUUID().toString();
        LocalDateTime leaseUntil = now.plusSeconds(LEASE_SECONDS);

        if (status == GlobalStatus.TRYING) {
            int transitioned = globals.transitionWithOwner(
                    xid, GlobalStatus.TRYING.name(), GlobalStatus.CANCELLING.name(),
                    owner, leaseUntil, now);
            if (transitioned != 1) {
                throw new TccException(TccResultCode.TCC_BUSY, "rollback state changed");
            }
            branches.updateToCancelling(xid, now, now);
            return new Prepared(xid, GlobalStatus.CANCELLING, owner, branches.findByXid(xid), false);
        }

        int claimed = globals.claimOwner(xid, owner, leaseUntil, now);
        if (claimed != 1) {
            throw new TccException(TccResultCode.TCC_BUSY, "cannot claim global owner");
        }
        return new Prepared(xid, GlobalStatus.CANCELLING, owner, branches.findByXid(xid), false);
    }

    private TccCommitResult executeConfirm(Prepared prepared) {
        for (TccBranchEntity branch : prepared.branches) {
            if (!Objects.equals(branch.getStatus(), BranchStatus.CONFIRMING.name())) {
                continue;
            }
            if (!renew(prepared.xid, prepared.owner)) {
                return new TccCommitResult(GlobalStatus.CONFIRMING);
            }
            TccCallbackResult result = participantClient.confirm(branch);
            if (result.result() == TccPhaseResult.RETRYABLE) {
                return new TccCommitResult(GlobalStatus.CONFIRMING);
            }
            if (result.result() == TccPhaseResult.PERMANENT_FAILURE) {
                return failCommitBranch(prepared, branch);
            }
            Boolean marked = transactionTemplate.execute(status ->
                    branches.markConfirmed(
                            prepared.xid, branch.getBranchId(), prepared.owner,
                            LocalDateTime.now(clock)) == 1);
            if (!Boolean.TRUE.equals(marked)) {
                return new TccCommitResult(GlobalStatus.CONFIRMING);
            }
        }
        return new TccCommitResult(finalizeStatus(
                prepared.xid, prepared.owner, GlobalStatus.CONFIRMING));
    }

    private TccRollbackResult executeCancel(Prepared prepared) {
        for (TccBranchEntity branch : prepared.branches) {
            if (!Objects.equals(branch.getStatus(), BranchStatus.CANCELLING.name())) {
                continue;
            }
            if (!renew(prepared.xid, prepared.owner)) {
                return new TccRollbackResult(GlobalStatus.CANCELLING);
            }
            TccCallbackResult result = participantClient.cancel(branch);
            if (result.result() == TccPhaseResult.RETRYABLE) {
                return new TccRollbackResult(GlobalStatus.CANCELLING);
            }
            if (result.result() == TccPhaseResult.PERMANENT_FAILURE) {
                return failCancelBranch(prepared, branch);
            }
            Boolean marked = transactionTemplate.execute(status ->
                    branches.markCancelled(
                            prepared.xid, branch.getBranchId(), prepared.owner,
                            LocalDateTime.now(clock)) == 1);
            if (!Boolean.TRUE.equals(marked)) {
                return new TccRollbackResult(GlobalStatus.CANCELLING);
            }
        }
        return new TccRollbackResult(finalizeStatus(
                prepared.xid, prepared.owner, GlobalStatus.CANCELLING));
    }

    private TccCommitResult failCommitBranch(
            Prepared prepared, TccBranchEntity branch) {
        Boolean failed = transactionTemplate.execute(status -> {
            int marked = branches.markDead(
                    prepared.xid, branch.getBranchId(), prepared.owner, LocalDateTime.now(clock));
            int transitioned = globals.transitionOwned(
                    prepared.xid, GlobalStatus.CONFIRMING.name(), GlobalStatus.FAILED.name(),
                    prepared.owner, LocalDateTime.now(clock));
            return marked == 1 && transitioned == 1;
        });
        return new TccCommitResult(Boolean.TRUE.equals(failed)
                ? GlobalStatus.FAILED : GlobalStatus.CONFIRMING);
    }

    private TccRollbackResult failCancelBranch(
            Prepared prepared, TccBranchEntity branch) {
        Boolean failed = transactionTemplate.execute(status -> {
            int marked = branches.markDead(
                    prepared.xid, branch.getBranchId(), prepared.owner, LocalDateTime.now(clock));
            int transitioned = globals.transitionOwned(
                    prepared.xid, GlobalStatus.CANCELLING.name(), GlobalStatus.FAILED.name(),
                    prepared.owner, LocalDateTime.now(clock));
            return marked == 1 && transitioned == 1;
        });
        return new TccRollbackResult(Boolean.TRUE.equals(failed)
                ? GlobalStatus.FAILED : GlobalStatus.CANCELLING);
    }

    private GlobalStatus finalizeStatus(long xid, String owner, GlobalStatus from) {
        GlobalStatus result = transactionTemplate.execute(status -> {
            TccGlobalEntity global = globals.findByXidForUpdate(xid);
            if (global == null || !Objects.equals(global.getOwnerToken(), owner)
                    || !Objects.equals(global.getStatus(), from.name())) {
                return from;
            }
            List<TccBranchEntity> current = branches.findByXid(xid);
            boolean anyDead = current.stream()
                    .anyMatch(branch -> BranchStatus.DEAD.name().equals(branch.getStatus()));
            if (anyDead) {
                int changed = globals.transitionOwned(xid, from.name(), GlobalStatus.FAILED.name(),
                        owner, LocalDateTime.now(clock));
                return changed == 1 ? GlobalStatus.FAILED : from;
            }
            boolean allDone = current.stream().allMatch(branch ->
                    from == GlobalStatus.CONFIRMING
                            ? BranchStatus.CONFIRMED.name().equals(branch.getStatus())
                            : BranchStatus.CANCELLED.name().equals(branch.getStatus())
                            || BranchStatus.SUSPENDED.name().equals(branch.getStatus()));
            if (!allDone) {
                return from;
            }
            GlobalStatus target = from == GlobalStatus.CONFIRMING
                    ? GlobalStatus.CONFIRMED
                    : GlobalStatus.CANCELLED;
            int changed = globals.transitionOwned(
                    xid, from.name(), target.name(), owner, LocalDateTime.now(clock));
            return changed == 1 ? target : from;
        });
        return result == null ? from : result;
    }

    private boolean renew(long xid, String owner) {
        LocalDateTime now = LocalDateTime.now(clock);
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                globals.renewOwner(xid, owner, now.plusSeconds(LEASE_SECONDS), now) == 1));
    }

    private boolean hasActiveOwner(TccGlobalEntity global, LocalDateTime now) {
        return global.getOwnerToken() != null
                && global.getLeaseUntil() != null
                && global.getLeaseUntil().isAfter(now);
    }

    private TccBranchInfo toBranchInfo(TccBranchEntity branch) {
        return new TccBranchInfo(
                branch.getBranchId(),
                branch.getResourceKey(),
                branch.getActionName(),
                parseBranch(branch.getStatus()),
                branch.getRetryCount() == null ? 0 : branch.getRetryCount());
    }

    private GlobalStatus parseGlobal(String status) {
        try {
            return GlobalStatus.valueOf(status);
        } catch (IllegalArgumentException failure) {
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "unknown global status: " + status, failure);
        }
    }

    private BranchStatus parseBranch(String status) {
        try {
            return BranchStatus.valueOf(status);
        } catch (IllegalArgumentException failure) {
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "unknown branch status: " + status, failure);
        }
    }

    private record Prepared(
            long xid,
            GlobalStatus status,
            String owner,
            List<TccBranchEntity> branches,
            boolean terminal) {
    }
}
