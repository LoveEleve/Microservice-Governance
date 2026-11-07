package com.wuba.crm.tx.consumer.lease;

public interface LeaseRegistrar {

    void register(LeaseTask task);

    void complete(LeaseTask task);
}
