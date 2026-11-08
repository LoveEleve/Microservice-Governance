package com.wuba.crm.tx.tcc.protocol.model;
import com.wuba.crm.tx.tcc.api.GlobalStatus;

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
