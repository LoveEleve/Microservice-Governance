package com.wuba.crm.tx.consumer.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

public final class ConsumerBootstrapManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ConsumerBootstrapManager.class);

    private final List<ConsumerBootstrap> bootstraps;
    private boolean started;

    public ConsumerBootstrapManager(List<ConsumerBootstrap> bootstraps) {
        this.bootstraps = List.copyOf(Objects.requireNonNull(bootstraps, "bootstraps"));
    }

    public void start() {
        if (started) {
            return;
        }
        int startedCount = 0;
        try {
            for (ConsumerBootstrap bootstrap : bootstraps) {
                bootstrap.start();
                startedCount++;
            }
            started = true;
        } catch (RuntimeException ex) {
            for (int i = 0; i < startedCount; i++) {
                bootstraps.get(i).close();
            }
            throw ex;
        }
    }

    public int size() {
        return bootstraps.size();
    }

    @Override
    public void close() {
        for (ConsumerBootstrap bootstrap : bootstraps) {
            try {
                bootstrap.close();
            } catch (RuntimeException ex) {
                log.warn("consumer bootstrap close failed", ex);
            }
        }
        started = false;
    }
}
