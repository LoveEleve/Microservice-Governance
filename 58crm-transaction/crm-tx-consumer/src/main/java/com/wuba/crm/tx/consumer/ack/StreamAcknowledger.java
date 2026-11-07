package com.wuba.crm.tx.consumer.ack;

public interface StreamAcknowledger {

    long acknowledge(String stream, String group, String... entryIds);
}
