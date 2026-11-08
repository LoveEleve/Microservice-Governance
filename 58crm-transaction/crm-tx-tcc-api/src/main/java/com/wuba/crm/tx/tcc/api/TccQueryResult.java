package com.wuba.crm.tx.tcc.api;

import java.util.List;
import java.util.Objects;

public record TccQueryResult(GlobalStatus status, List<TccBranchInfo> branches) {

    public TccQueryResult {
        status = Objects.requireNonNull(status, "status");
        branches = List.copyOf(Objects.requireNonNull(branches, "branches"));
    }
}
