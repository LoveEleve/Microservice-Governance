package com.wuba.crm.tx.tcc.api;

/**
 * TCC 操作结果码。
 *
 * <p>区分可重试和不可重试的失败，调用方据此决定短退避还是进入人工/失败终态。</p>
 */
public enum TccResultCode {
    OK,
    TCC_INVALID_ARGUMENT,
    TCC_NOT_READY,
    TCC_INVALID_STATE,
    TCC_BUSY,
    TCC_NOT_FOUND,
    TCC_DUPLICATE,
    TCC_RETRY_EXHAUSTED,
    TCC_CALLBACK_FAILED,
    TCC_INTERNAL_ERROR;

    /**
     * 是否为可重试结果。
     *
     * @return true 表示调用方可以做短退避重试
     */
    public boolean isRetryable() {
        return this == TCC_NOT_READY || this == TCC_BUSY || this == TCC_CALLBACK_FAILED;
    }
}
