package com.wuba.crm.tx.tcc.api;

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
