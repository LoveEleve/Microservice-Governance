package com.wuba.crm.tx.consumer.domain;

public enum ConsumeStatus {
    READY,
    PROCESSING,
    CONSUMED,
    DEAD;

    public boolean isTerminal() {
        return this == CONSUMED || this == DEAD;
    }
}
