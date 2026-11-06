package com.wuba.crm.tx.outbox.config;

/**
 * 发布/消费了未注册的业务线。
 */
public class UnknownBizCodeException extends RuntimeException {

    public UnknownBizCodeException(String bizCode) {
        super("unknown bizCode: " + bizCode);
    }
}
