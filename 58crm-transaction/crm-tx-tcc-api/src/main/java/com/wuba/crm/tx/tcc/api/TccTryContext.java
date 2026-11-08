package com.wuba.crm.tx.tcc.api;

/**
 * TM 执行业务 Try 时拿到的上下文。
 *
 * @param xid             全局事务号
 * @param branchId        分支号
 * @param resourceKey     分支幂等键
 * @param actionName      TCC 动作名
 * @param applicationData 业务上下文快照
 */
public record TccTryContext(
        long xid,
        long branchId,
        String resourceKey,
        String actionName,
        String applicationData) {

    public TccTryContext {
        xid = TccValidation.requirePositive(xid, "xid");
        branchId = TccValidation.requirePositive(branchId, "branchId");
        resourceKey = TccValidation.requireText(resourceKey, "resourceKey", 128);
        actionName = TccValidation.requireText(actionName, "actionName", 128);
        applicationData = TccValidation.applicationData(applicationData);
    }
}
