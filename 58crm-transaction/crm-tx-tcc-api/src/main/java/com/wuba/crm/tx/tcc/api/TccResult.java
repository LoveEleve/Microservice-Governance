package com.wuba.crm.tx.tcc.api;

import java.util.List;
import java.util.Objects;

/**
 * 一次 TCC 执行的结果。
 *
 * @param xid      全局事务号
 * @param status   全局状态
 * @param branches 分支结果
 * @param message  失败/状态说明
 */
public record TccResult(
        long xid,
        GlobalStatus status,
        List<TccBranchResult> branches,
        String message) {

    public TccResult {
        xid = TccValidation.requirePositive(xid, "xid");
        status = Objects.requireNonNull(status, "status");
        branches = List.copyOf(Objects.requireNonNull(branches, "branches"));
        message = message == null ? "" : message;
    }

    /**
     * 是否已经确认。
     *
     * @return true 表示 CONFIRMED
     */
    public boolean isConfirmed() {
        return status == GlobalStatus.CONFIRMED;
    }
}
