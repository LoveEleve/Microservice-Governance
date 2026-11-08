package com.wuba.crm.tx.tcc.api;

/**
 * TCC 分支事务状态。
 *
 * <p>分支状态由 Try 上报和 TC 的二阶段推进共同决定；DEAD 表示重试耗尽，
 * 对应全局 FAILED。SUSPENDED 表示 Cancel 先于 Try 到达的空回滚标记。</p>
 */
public enum BranchStatus {
    TRYING,
    TRY_SUCCESS,
    TRY_FAIL,
    CONFIRMING,
    CANCELLING,
    CONFIRMED,
    CANCELLED,
    SUSPENDED,
    DEAD;

    /**
     * 判断分支状态能否迁移到目标状态。
     *
     * @param target 目标状态
     * @return true 表示合法迁移
     */
    public boolean canTransitionTo(BranchStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case TRYING -> target == TRY_SUCCESS
                    || target == TRY_FAIL
                    || target == CANCELLING;
            case TRY_SUCCESS -> target == CONFIRMING || target == CANCELLING;
            case TRY_FAIL -> target == CANCELLING;
            case CONFIRMING -> target == CONFIRMED || target == DEAD;
            case CANCELLING -> target == CANCELLED
                    || target == SUSPENDED
                    || target == DEAD;
            case CONFIRMED, CANCELLED, SUSPENDED, DEAD -> false;
        };
    }

    /**
     * 是否为终态。
     *
     * @return true 表示不会再有后续迁移
     */
    public boolean isTerminal() {
        return this == CONFIRMED
                || this == CANCELLED
                || this == SUSPENDED
                || this == DEAD;
    }

    /**
     * Cancel 是否已经完成（含空回滚的 SUSPENDED）。
     *
     * @return true 表示可以计入全局 CANCELLED 收敛
     */
    public boolean isCancelFinished() {
        return this == CANCELLED || this == SUSPENDED;
    }
}
