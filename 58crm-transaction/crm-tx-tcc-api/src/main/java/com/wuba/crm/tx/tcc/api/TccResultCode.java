package com.wuba.crm.tx.tcc.api;

public enum TccResultCode {
    OK,
    TCC_NOT_READY,
    TCC_INVALID_STATE,
    TCC_BUSY,
    TCC_NOT_FOUND,
    TCC_DUPLICATE,
    TCC_RETRY_EXHAUSTED,
    TCC_CALLBACK_FAILED;

    public boolean isRetryable() {
        return this == TCC_NOT_READY || this == TCC_BUSY || this == TCC_CALLBACK_FAILED;
    }
}
