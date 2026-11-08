package com.wuba.crm.tx.tcc.api;

import java.util.Objects;

/**
 * 开启全局事务的结果。
 *
 * @param xid    全局事务号
 * @param status 初始全局状态
 */
public record TccBeginResult(long xid, GlobalStatus status) {

    public TccBeginResult {
        if (xid <= 0) {
            throw new IllegalArgumentException("xid must be positive");
        }
        status = Objects.requireNonNull(status, "status");
    }
}
