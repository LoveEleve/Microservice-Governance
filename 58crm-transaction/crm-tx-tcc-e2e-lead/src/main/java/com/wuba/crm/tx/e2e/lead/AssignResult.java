package com.wuba.crm.tx.e2e.lead;

/**
 * 分配结果。
 *
 * @param xid    全局事务号
 * @param status 最终状态
 */
public record AssignResult(long xid, String status) {
}
