package com.wuba.crm.tx.tcc.api;

import java.util.Objects;

/**
 * Rollback 请求的结果。
 *
 * @param status 当前全局状态
 */
public record TccRollbackResult(GlobalStatus status) {

    public TccRollbackResult {
        status = Objects.requireNonNull(status, "status");
    }
}
