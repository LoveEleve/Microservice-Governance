package com.wuba.crm.tx.e2e.quota;

/**
 * quota Try 请求。
 *
 * @param xid      全局事务号
 * @param branchId 分支号
 * @param userId   用户
 */
public record TryQuotaRequest(long xid, long branchId, long userId) {
}
