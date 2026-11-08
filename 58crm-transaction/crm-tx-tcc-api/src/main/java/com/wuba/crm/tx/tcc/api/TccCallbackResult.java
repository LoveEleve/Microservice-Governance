package com.wuba.crm.tx.tcc.api;

import java.util.Objects;

public record TccCallbackResult(TccPhaseResult result, String message) {

    public TccCallbackResult {
        result = Objects.requireNonNull(result, "result");
        message = message == null ? "" : message;
    }

    public static TccCallbackResult success() {
        return new TccCallbackResult(TccPhaseResult.SUCCESS, "");
    }

    public static TccCallbackResult retryable(String message) {
        return new TccCallbackResult(TccPhaseResult.RETRYABLE, message);
    }

    public static TccCallbackResult permanentFailure(String message) {
        return new TccCallbackResult(TccPhaseResult.PERMANENT_FAILURE, message);
    }
}
