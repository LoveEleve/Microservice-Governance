package com.wuba.crm.tx.tcc.api;

/**
 * 传给 {@link TccAction} 的业务动作上下文。
 *
 * @param xid             全局事务号
 * @param branchId        分支号
 * @param actionName      TCC 动作名
 * @param applicationData Confirm/Cancel 需要的业务上下文快照
 */
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
