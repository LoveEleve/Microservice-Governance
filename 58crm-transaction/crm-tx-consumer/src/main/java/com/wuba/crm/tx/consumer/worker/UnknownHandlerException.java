package com.wuba.crm.tx.consumer.worker;

public class UnknownHandlerException extends RuntimeException {

    public UnknownHandlerException(String bizCode, String eventType) {
        super("no consumer handler for bizCode=" + bizCode + ", eventType=" + eventType);
    }
}
