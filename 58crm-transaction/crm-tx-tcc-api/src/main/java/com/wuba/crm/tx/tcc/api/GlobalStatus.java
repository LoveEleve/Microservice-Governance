package com.wuba.crm.tx.tcc.api;

public enum GlobalStatus {
    TRYING,
    CONFIRMING,
    CANCELLING,
    CONFIRMED,
    CANCELLED,
    FAILED;

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

    public boolean isTerminal() {
        return this == CONFIRMED || this == CANCELLED || this == FAILED;
    }
}
