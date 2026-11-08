package com.wuba.crm.tx.e2e.quota;

/**
 * quota Try 响应。
 *
 * @param success 是否成功
 * @param message 说明
 */
public record TryQuotaResponse(boolean success, String message) {
}
