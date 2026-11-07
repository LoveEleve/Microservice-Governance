package com.wuba.crm.tx.consumer.persistence;

import java.time.Instant;

public interface IdempotentRepository {

    boolean tryAcquire(String msgId, String bizKey, Instant now);
}
