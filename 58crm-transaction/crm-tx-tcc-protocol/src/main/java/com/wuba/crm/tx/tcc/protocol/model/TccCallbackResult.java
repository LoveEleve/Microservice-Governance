package com.wuba.crm.tx.tcc.protocol.model;
import com.wuba.crm.tx.tcc.api.TccPhaseResult;

import java.util.Objects;

/**
 * RM Confirm/Cancel 回调的返回模型。
 */
public record TccCallbackResult(TccPhaseResult result, String message) {

    public TccCallbackResult {
        result = Objects.requireNonNull(result, "result");
        message = message == null ? "" : message;
    }

    /**
     * 构造成功结果。
     *
     * @return SUCCESS 结果
     */
    public static TccCallbackResult success() {
        return new TccCallbackResult(TccPhaseResult.SUCCESS, "");
    }

    /**
     * 构造可重试结果。
     *
     * @param message 失败原因
     * @return RETRYABLE 结果
     */
    public static TccCallbackResult retryable(String message) {
        return new TccCallbackResult(TccPhaseResult.RETRYABLE, message);
    }

    /**
     * 构造永久失败结果。
     *
     * @param message 业务失败原因
     * @return PERMANENT_FAILURE 结果
     */
    public static TccCallbackResult permanentFailure(String message) {
        return new TccCallbackResult(TccPhaseResult.PERMANENT_FAILURE, message);
    }
}
