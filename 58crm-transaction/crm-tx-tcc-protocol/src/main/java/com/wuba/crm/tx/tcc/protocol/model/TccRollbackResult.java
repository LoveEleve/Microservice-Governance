package com.wuba.crm.tx.tcc.protocol.model;
import com.wuba.crm.tx.tcc.api.GlobalStatus;

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
