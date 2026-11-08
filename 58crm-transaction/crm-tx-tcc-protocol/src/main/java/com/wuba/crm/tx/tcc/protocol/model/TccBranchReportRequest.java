package com.wuba.crm.tx.tcc.protocol.model;
import com.wuba.crm.tx.tcc.api.BranchStatus;
import com.wuba.crm.tx.tcc.api.TccValidation;

import java.util.Objects;

/**
 * TM 上报 Try 结果的请求。
 *
 * <p>只允许 TRY_SUCCESS 和 TRY_FAIL 两种状态；其他状态会被拒绝。</p>
 */
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
