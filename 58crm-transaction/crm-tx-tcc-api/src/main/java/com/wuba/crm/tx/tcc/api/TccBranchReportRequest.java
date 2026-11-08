package com.wuba.crm.tx.tcc.api;

import java.util.Objects;

public record TccBranchReportRequest(
        long xid,
        long branchId,
        BranchStatus status,
        String message) {

    public TccBranchReportRequest {
        xid = TccValidation.requirePositive(xid, "xid");
        branchId = TccValidation.requirePositive(branchId, "branchId");
        status = Objects.requireNonNull(status, "status");
        if (status != BranchStatus.TRY_SUCCESS && status != BranchStatus.TRY_FAIL) {
            throw new IllegalArgumentException("report status must be TRY_SUCCESS or TRY_FAIL");
        }
        message = TccValidation.optionalText(message, "message", 512);
    }
}
