package com.wuba.crm.tx.tcc.api;

import java.util.Objects;

public record TccBeginResult(long xid, GlobalStatus status) {

    public TccBeginResult {
        if (xid <= 0) {
            throw new IllegalArgumentException("xid must be positive");
        }
        status = Objects.requireNonNull(status, "status");
    }
}
