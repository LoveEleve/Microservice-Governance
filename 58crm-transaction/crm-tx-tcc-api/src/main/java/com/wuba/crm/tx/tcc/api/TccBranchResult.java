package com.wuba.crm.tx.tcc.api;

/**
 * 一次 TCC 执行中的分支结果。
 *
 * @param resourceKey 分支幂等键
 * @param branchId    分支号
 * @param status      分支最终观察到的状态
 */
public record TccBranchResult(
        String resourceKey,
        long branchId,
        BranchStatus status) {

    public TccBranchResult {
        resourceKey = TccValidation.requireText(resourceKey, "resourceKey", 128);
        branchId = TccValidation.requirePositive(branchId, "branchId");
        status = java.util.Objects.requireNonNull(status, "status");
    }
}
