package com.wuba.crm.tx.tcc.api;

import java.util.Objects;

public record TccRollbackResult(GlobalStatus status) {

    public TccRollbackResult {
        status = Objects.requireNonNull(status, "status");
    }
}
