package com.wuba.crm.tx.tcc.api;

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

    public boolean isTerminal() {
        return this == CONFIRMED
                || this == CANCELLED
                || this == SUSPENDED
                || this == DEAD;
    }

    public boolean isCancelFinished() {
        return this == CANCELLED || this == SUSPENDED;
    }
}
