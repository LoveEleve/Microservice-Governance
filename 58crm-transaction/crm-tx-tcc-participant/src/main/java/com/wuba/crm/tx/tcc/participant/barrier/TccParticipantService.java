package com.wuba.crm.tx.tcc.participant.barrier;

import com.wuba.crm.tx.tcc.api.BarrierStatus;
import com.wuba.crm.tx.tcc.api.BusinessActionContext;
import com.wuba.crm.tx.tcc.api.TccAction;
import com.wuba.crm.tx.tcc.api.TccException;
import com.wuba.crm.tx.tcc.api.TccPhaseResult;
import com.wuba.crm.tx.tcc.api.TccResultCode;
import com.wuba.crm.tx.tcc.api.TccTryResult;
import com.wuba.crm.tx.tcc.participant.action.TccActionRegistry;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * RM 本地 TCC 执行器。
 *
 * <p>barrier 状态和业务动作在同一个本地事务内。Try 失败会显式标记
 * rollback-only，确保不会留下一条“TRIED 但业务没预占”的记录。</p>
 */
@Service
public class TccParticipantService {

    private final BarrierRepository barriers;
    private final TccActionRegistry actionRegistry;

    /**
     * 创建执行器。
     *
     * @param barriers       barrier 仓储
     * @param actionRegistry 动作注册表
     */
    public TccParticipantService(BarrierRepository barriers, TccActionRegistry actionRegistry) {
        if (barriers == null || actionRegistry == null) {
            throw new IllegalArgumentException("participant dependencies must not be null");
        }
        this.barriers = barriers;
        this.actionRegistry = actionRegistry;
    }

    /**
     * 执行业务 Try。
     *
     * @param context 分支上下文
     * @return Try 结果
     */
    @Transactional
    public TccTryResult tryAction(BusinessActionContext context) {
        BarrierEntity existing = barriers.findForUpdate(context.xid(), context.branchId());
        if (existing != null) {
            return handleExistingTry(existing, context);
        }

        TccAction action = actionRegistry.require(context.actionName());
        BarrierEntity barrier = barrier(context, BarrierStatus.TRIED);
        try {
            barriers.insert(barrier);
        } catch (DuplicateKeyException duplicate) {
            BarrierEntity winner = barriers.findForUpdate(context.xid(), context.branchId());
            if (winner == null) {
                throw duplicate;
            }
            return handleExistingTry(winner, context);
        }

        TccTryResult result = action.tryAction(context);
        if (result != TccTryResult.SUCCESS) {
            // 业务预占失败时 barrier 必须一起回滚。
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return result;
    }

    /**
     * 执行 Confirm。
     *
     * @param context 分支上下文
     * @return Confirm 结果
     */
    @Transactional
    public TccPhaseResult confirm(BusinessActionContext context) {
        BarrierEntity barrier = barriers.findForUpdate(context.xid(), context.branchId());
        if (barrier == null) {
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "barrier not found");
        }
        assertSameAction(barrier, context);

        BarrierStatus status = parse(barrier.getStatus());
        if (status == BarrierStatus.CONFIRMED) {
            return TccPhaseResult.SUCCESS;
        }
        if (status == BarrierStatus.CANCELLED || status == BarrierStatus.SUSPENDED) {
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "barrier already " + status);
        }

        TccAction action = actionRegistry.require(context.actionName());
        TccPhaseResult result = action.confirmAction(context);
        if (result != TccPhaseResult.SUCCESS) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return result;
        }

        int updated = barriers.markConfirmed(
                context.xid(), context.branchId(), LocalDateTime.now(ZoneOffset.UTC));
        if (updated != 1) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "barrier confirm state changed");
        }
        return TccPhaseResult.SUCCESS;
    }

    /**
     * 执行 Cancel。
     *
     * @param context 分支上下文
     * @return Cancel 结果
     */
    @Transactional
    public TccPhaseResult cancel(BusinessActionContext context) {
        BarrierEntity barrier = barriers.findForUpdate(context.xid(), context.branchId());
        if (barrier == null) {
            // 空回滚也要确认 action 存在，避免未知 action 抢先写入 SUSPENDED。
            actionRegistry.require(context.actionName());
            try {
                barriers.insert(barrier(context, BarrierStatus.SUSPENDED));
                return TccPhaseResult.SUCCESS;
            } catch (DuplicateKeyException duplicate) {
                BarrierEntity winner = barriers.findForUpdate(context.xid(), context.branchId());
                if (winner == null) {
                    throw duplicate;
                }
                return cancelExisting(winner, context);
            }
        }
        return cancelExisting(barrier, context);
    }

    private TccTryResult handleExistingTry(
            BarrierEntity barrier, BusinessActionContext context) {
        assertSameAction(barrier, context);
        return switch (parse(barrier.getStatus())) {
            case TRIED, CONFIRMED -> TccTryResult.SUCCESS;
            case CANCELLED, SUSPENDED -> TccTryResult.FAILURE;
        };
    }

    private TccPhaseResult cancelExisting(
            BarrierEntity barrier, BusinessActionContext context) {
        assertSameAction(barrier, context);
        BarrierStatus status = parse(barrier.getStatus());
        if (status == BarrierStatus.CANCELLED || status == BarrierStatus.SUSPENDED) {
            return TccPhaseResult.SUCCESS;
        }
        if (status == BarrierStatus.CONFIRMED) {
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "barrier already CONFIRMED");
        }

        TccAction action = actionRegistry.require(context.actionName());
        TccPhaseResult result = action.cancelAction(context);
        if (result != TccPhaseResult.SUCCESS) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return result;
        }
        int updated = barriers.markCancelled(
                context.xid(), context.branchId(), LocalDateTime.now(ZoneOffset.UTC));
        if (updated != 1) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "barrier cancel state changed");
        }
        return TccPhaseResult.SUCCESS;
    }

    private BarrierEntity barrier(BusinessActionContext context, BarrierStatus status) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        BarrierEntity barrier = new BarrierEntity();
        barrier.setXid(context.xid());
        barrier.setBranchId(context.branchId());
        barrier.setActionName(context.actionName());
        barrier.setStatus(status.name());
        barrier.setCreatedAt(now);
        barrier.setUpdatedAt(now);
        return barrier;
    }

    private void assertSameAction(BarrierEntity barrier, BusinessActionContext context) {
        if (!barrier.getActionName().equals(context.actionName())) {
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "actionName mismatch for barrier");
        }
    }

    private BarrierStatus parse(String status) {
        try {
            return BarrierStatus.valueOf(status);
        } catch (IllegalArgumentException failure) {
            throw new TccException(TccResultCode.TCC_INVALID_STATE,
                    "unknown barrier status: " + status, failure);
        }
    }
}
