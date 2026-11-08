package com.wuba.crm.tx.tcc.protocol.model;
import com.wuba.crm.tx.tcc.api.BranchStatus;

import java.util.Objects;

/**
 * 注册分支事务的结果。
 *
 * @param branchId 分支号
 * @param status   分支初始状态
 */
public record TccBranchRegisterResult(long branchId, BranchStatus status) {

    public TccBranchRegisterResult {
        if (branchId <= 0) {
            throw new IllegalArgumentException("branchId must be positive");
        }
        status = Objects.requireNonNull(status, "status");
    }
}
