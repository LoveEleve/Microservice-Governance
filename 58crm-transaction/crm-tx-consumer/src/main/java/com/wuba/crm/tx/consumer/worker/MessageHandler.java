package com.wuba.crm.tx.consumer.worker;

import com.wuba.crm.tx.consumer.domain.ConsumedMessage;

@FunctionalInterface
public interface MessageHandler {

    void handle(ConsumedMessage message) throws Exception;
}
