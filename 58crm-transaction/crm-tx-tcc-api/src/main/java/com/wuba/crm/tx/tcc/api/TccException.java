package com.wuba.crm.tx.tcc.api;

/**
 * TCC 业务异常，携带可判定的结果码。
 */
public class TccException extends RuntimeException {

    private final TccResultCode code;

    /**
     * 创建异常。
     *
     * @param code    结果码
     * @param message 异常信息
     */
    public TccException(TccResultCode code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 创建带原因的异常。
     *
     * @param code    结果码
     * @param message 异常信息
     * @param cause   原始异常
     */
    public TccException(TccResultCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 返回业务结果码。
     *
     * @return 结果码
     */
    public TccResultCode getCode() {
        return code;
    }
}
