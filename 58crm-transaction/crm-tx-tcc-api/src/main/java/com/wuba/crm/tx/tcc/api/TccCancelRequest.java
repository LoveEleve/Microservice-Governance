package com.wuba.crm.tx.tcc.api;

public record TccCancelRequest(
        long xid,
        long branchId,
        String actionName,
        String applicationData) {

    public TccCancelRequest {
        xid = TccValidation.requirePositive(xid, "xid");
        branchId = TccValidation.requirePositive(branchId, "branchId");
        actionName = TccValidation.requireText(actionName, "actionName", 128);
        applicationData = TccValidation.applicationData(applicationData);
    }
}
