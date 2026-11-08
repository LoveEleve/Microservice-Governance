package com.wuba.crm.tx.tcc.participant.web;

/**
 * 通用 Try endpoint 请求。
 *
 * @param xid             全局事务号
 * @param branchId        分支号
 * @param actionName      TCC 动作名
 * @param applicationData 业务上下文
 */
public record TccTryRequest(
        long xid,
        long branchId,
        String actionName,
        String applicationData) {
}
