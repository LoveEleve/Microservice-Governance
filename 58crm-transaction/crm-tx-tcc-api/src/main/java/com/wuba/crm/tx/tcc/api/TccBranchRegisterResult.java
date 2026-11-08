package com.wuba.crm.tx.tcc.api;

import java.util.Objects;

public record TccBranchRegisterResult(long branchId, BranchStatus status) {

    public TccBranchRegisterResult {
        if (branchId <= 0) {
            throw new IllegalArgumentException("branchId must be positive");
        }
        status = Objects.requireNonNull(status, "status");
    }
}
