package com.wuba.crm.tx.tcc.api;

import java.util.Objects;

/**
 * Commit 请求的结果。
 *
 * @param status 当前全局状态
 */
public record TccCommitResult(GlobalStatus status) {

    public TccCommitResult {
        status = Objects.requireNonNull(status, "status");
    }
}
