package com.wuba.crm.tx.tcc.example.lead;

/**
 * coupon 通用 /tcc/try 请求。
 *
 * @param xid             全局事务号
 * @param branchId        分支号
 * @param actionName      动作名
 * @param applicationData 业务上下文
 */
public record TryCouponRequest(
        long xid,
        long branchId,
        String actionName,
        String applicationData) {
}
