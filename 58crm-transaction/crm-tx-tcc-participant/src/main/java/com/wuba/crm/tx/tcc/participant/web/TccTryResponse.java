package com.wuba.crm.tx.tcc.participant.web;

/**
 * 通用 Try endpoint 响应。
 *
 * @param success 是否成功
 * @param message 结果说明
 */
public record TccTryResponse(boolean success, String message) {
}
