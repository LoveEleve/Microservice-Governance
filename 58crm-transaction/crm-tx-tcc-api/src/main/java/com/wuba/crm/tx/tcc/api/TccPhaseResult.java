package com.wuba.crm.tx.tcc.api;

/**
 * Confirm/Cancel 回调的处理结果。
 *
 * <p>SUCCESS 表示终态已落地；RETRYABLE 表示稍后可重试；
 * PERMANENT_FAILURE 表示业务明确拒绝，继续重试没有意义。</p>
 */
public enum TccPhaseResult {
    SUCCESS,
    RETRYABLE,
    PERMANENT_FAILURE
}
