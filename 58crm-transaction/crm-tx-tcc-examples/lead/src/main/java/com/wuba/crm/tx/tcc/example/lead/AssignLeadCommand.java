package com.wuba.crm.tx.tcc.example.lead;

/**
 * 分配线索命令。
 *
 * @param requestId 业务幂等键
 * @param leadId    线索
 * @param userId    用户
 * @param couponId  优惠券
 */
public record AssignLeadCommand(String requestId, long leadId, long userId, long couponId) {
}
