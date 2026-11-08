package com.wuba.crm.tx.tcc.api;

public class TccException extends RuntimeException {

    private final TccResultCode code;

    public TccException(TccResultCode code, String message) {
        super(message);
        this.code = code;
    }

    public TccException(TccResultCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public TccResultCode getCode() {
        return code;
    }
}
