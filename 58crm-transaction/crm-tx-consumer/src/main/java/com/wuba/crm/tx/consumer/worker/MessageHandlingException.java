package com.wuba.crm.tx.consumer.worker;

public class MessageHandlingException extends RuntimeException {

    public MessageHandlingException(String msgId, Throwable cause) {
        super("consume handler failed: " + msgId, cause);
    }
}
