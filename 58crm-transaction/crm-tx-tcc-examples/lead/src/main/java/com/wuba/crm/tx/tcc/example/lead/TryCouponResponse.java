package com.wuba.crm.tx.tcc.example.lead;

/**
 * coupon Try 响应。
 *
 * @param success 是否成功
 * @param message 说明
 */
public record TryCouponResponse(boolean success, String message) {
}
