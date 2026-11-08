package com.wuba.crm.tx.tcc.api;

/**
 * Try 本地执行结果。
 *
 * <p>Try 是业务 API，结果由 TM 通过 ReportBranch 上报给 TC。</p>
 */
public enum TccTryResult {
    SUCCESS,
    FAILURE
}
