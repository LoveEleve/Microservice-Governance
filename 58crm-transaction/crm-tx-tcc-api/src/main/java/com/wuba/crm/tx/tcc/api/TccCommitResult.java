package com.wuba.crm.tx.tcc.api;

import java.util.Objects;

public record TccCommitResult(GlobalStatus status) {

    public TccCommitResult {
        status = Objects.requireNonNull(status, "status");
    }
}
