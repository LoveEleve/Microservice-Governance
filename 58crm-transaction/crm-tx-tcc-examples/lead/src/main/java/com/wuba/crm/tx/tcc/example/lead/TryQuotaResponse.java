package com.wuba.crm.tx.tcc.example.lead;

/**
 * quota Try 响应。
 *
 * @param success 是否成功
 * @param message 说明
 */
public record TryQuotaResponse(boolean success, String message) {
}
