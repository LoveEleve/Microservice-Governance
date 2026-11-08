package com.wuba.crm.tx.tcc.protocol.model;

import com.wuba.crm.tx.tcc.api.TccValidation;
/**
 * 注册分支事务的请求。
 *
 * @param xid             全局事务号
 * @param resourceKey     分支幂等键
 * @param actionName      TCC 动作名
 * @param callbackEndpoint RM 回调地址
 * @param applicationData Confirm/Cancel 上下文，最大 64KB
 */
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
