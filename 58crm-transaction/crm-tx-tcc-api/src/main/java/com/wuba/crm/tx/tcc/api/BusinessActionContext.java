package com.wuba.crm.tx.tcc.api;

public record BusinessActionContext(
        long xid,
        long branchId,
        String actionName,
        String applicationData) {

    public BusinessActionContext {
        xid = TccValidation.requirePositive(xid, "xid");
        branchId = TccValidation.requirePositive(branchId, "branchId");
        actionName = TccValidation.requireText(actionName, "actionName", 128);
        applicationData = TccValidation.applicationData(applicationData);
    }
}
