package com.wuba.crm.tx.tcc.example.lead;

/**
 * 分配结果。
 *
 * @param xid    全局事务号
 * @param status 最终状态
 */
public record AssignResult(long xid, String status) {
}
