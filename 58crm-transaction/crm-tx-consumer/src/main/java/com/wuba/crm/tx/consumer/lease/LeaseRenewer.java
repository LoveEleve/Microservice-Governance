package com.wuba.crm.tx.consumer.lease;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@FunctionalInterface
public interface LeaseRenewer {

    boolean renew(LeaseTask task);

    default Set<String> renewBatch(List<LeaseTask> tasks) {
        Set<String> renewed = new HashSet<>();
        for (LeaseTask task : tasks) {
            if (renew(task)) {
                renewed.add(task.key());
            }
        }
        return renewed;
    }
}
