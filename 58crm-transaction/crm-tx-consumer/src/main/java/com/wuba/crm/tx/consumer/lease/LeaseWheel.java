package com.wuba.crm.tx.consumer.lease;

import java.time.Duration;
import java.util.function.Consumer;

public interface LeaseWheel extends AutoCloseable {

    void schedule(LeaseTask task, Duration delay, Consumer<LeaseTask> onDue);

    void cancel(LeaseTask task);

    @Override
    void close();
}
