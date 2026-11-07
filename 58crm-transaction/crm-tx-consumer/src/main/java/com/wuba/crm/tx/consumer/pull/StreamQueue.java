package com.wuba.crm.tx.consumer.pull;

import com.wuba.crm.tx.consumer.domain.ConsumedMessage;

import java.time.Duration;
import java.util.List;

public interface StreamQueue {

    void putAll(List<ConsumedMessage> messages) throws InterruptedException;

    List<ConsumedMessage> takeBatch(int max, Duration timeout) throws InterruptedException;

    int size();

    void clear();
}
