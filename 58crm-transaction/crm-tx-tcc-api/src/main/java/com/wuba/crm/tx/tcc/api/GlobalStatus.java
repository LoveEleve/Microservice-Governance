package com.wuba.crm.tx.tcc.api;

/**
 * TCC 全局事务状态。
 *
 * <p>状态只能沿合法方向推进：</p>
 *
 * <pre>
 * TRYING -> CONFIRMING -> CONFIRMED
 *        -> CANCELLING -> CANCELLED
 * CONFIRMING/CANCELLING -> FAILED
 * </pre>
 */
public enum GlobalStatus {
    TRYING,
    CONFIRMING,
    CANCELLING,
    CONFIRMED,
    CANCELLED,
    FAILED;

    /**
     * 判断当前状态能否迁移到目标状态。
     *
     * @param target 目标状态
     * @return true 表示合法迁移
     */
    public boolean canTransitionTo(GlobalStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case TRYING -> target == CONFIRMING || target == CANCELLING;
            case CONFIRMING -> target == CONFIRMED || target == FAILED;
            case CANCELLING -> target == CANCELLED || target == FAILED;
            case CONFIRMED, CANCELLED, FAILED -> false;
        };
    }

    /**
     * 是否为终态。
     *
     * @return true 表示不会再有后续迁移
     */
    public boolean isTerminal() {
        return this == CONFIRMED || this == CANCELLED || this == FAILED;
    }
}
