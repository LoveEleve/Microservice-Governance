package com.wuba.crm.tx.consumer.worker;

public class LeaseLostException extends RuntimeException {

    public LeaseLostException(String msgId) {
        super("consume lease lost: " + msgId);
    }
}
