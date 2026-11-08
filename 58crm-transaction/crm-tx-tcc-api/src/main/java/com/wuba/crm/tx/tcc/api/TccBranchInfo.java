package com.wuba.crm.tx.tcc.api;

import java.util.Objects;

/**
 * 查询结果中的单个分支快照。
 */
public record TccBranchInfo(
        long branchId,
        String resourceKey,
        String actionName,
        BranchStatus status,
        int retryCount) {

    public TccBranchInfo {
        branchId = TccValidation.requirePositive(branchId, "branchId");
        resourceKey = TccValidation.requireText(resourceKey, "resourceKey", 128);
        actionName = TccValidation.requireText(actionName, "actionName", 128);
        status = Objects.requireNonNull(status, "status");
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
    }
}
