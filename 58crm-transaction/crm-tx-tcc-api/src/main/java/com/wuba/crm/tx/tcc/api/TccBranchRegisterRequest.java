package com.wuba.crm.tx.tcc.api;

public record TccBranchRegisterRequest(
        long xid,
        String resourceKey,
        String actionName,
        String callbackEndpoint,
        String applicationData) {

    public TccBranchRegisterRequest {
        xid = TccValidation.requirePositive(xid, "xid");
        resourceKey = TccValidation.requireText(resourceKey, "resourceKey", 128);
        actionName = TccValidation.requireText(actionName, "actionName", 128);
        callbackEndpoint = TccValidation.requireText(callbackEndpoint, "callbackEndpoint", 256);
        applicationData = TccValidation.applicationData(applicationData);
    }
}
