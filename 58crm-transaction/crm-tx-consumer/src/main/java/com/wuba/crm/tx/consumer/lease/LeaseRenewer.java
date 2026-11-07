package com.wuba.crm.tx.consumer.lease;

@FunctionalInterface
public interface LeaseRenewer {

    boolean renew(LeaseTask task);
}
