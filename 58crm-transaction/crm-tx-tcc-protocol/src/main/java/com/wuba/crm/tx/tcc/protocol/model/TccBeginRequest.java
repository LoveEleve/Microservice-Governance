package com.wuba.crm.tx.tcc.protocol.model;

import com.wuba.crm.tx.tcc.api.TccValidation;
/**
 * 开启全局事务的请求。
 *
 * @param requestId        begin 幂等键
 * @param bizCode          业务域
 * @param bizId            业务单号
 * @param tryTimeoutSeconds Try 阶段超时秒数
 */
public record TccBeginRequest(
        String requestId,
        String bizCode,
        String bizId,
        int tryTimeoutSeconds) {

    public TccBeginRequest {
        requestId = TccValidation.requireText(requestId, "requestId", 128);
        bizCode = TccValidation.requireText(bizCode, "bizCode", 64);
        bizId = TccValidation.requireText(bizId, "bizId", 128);
        if (tryTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("tryTimeoutSeconds must be positive");
        }
    }
}
