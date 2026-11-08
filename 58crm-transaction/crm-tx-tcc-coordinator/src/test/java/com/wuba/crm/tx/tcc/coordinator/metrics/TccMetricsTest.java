package com.wuba.crm.tx.tcc.coordinator.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证无 registry 降级和基础指标计数。
 */
class TccMetricsTest {

    @Test
    void scanAndRetry_countersAreRecorded() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TccMetrics metrics = new TccMetrics(registry);

        metrics.scan("due", 2);
        metrics.scan("timeout", 1);
        metrics.retry("confirm", "ok");
        metrics.retry("confirm", "skip");

        assertEquals(2.0, registry.get("crm.tx.tcc.scan").tag("kind", "due")
                .counter().count());
        assertEquals(1.0, registry.get("crm.tx.tcc.scan").tag("kind", "timeout")
                .counter().count());
        assertEquals(1.0, registry.get("crm.tx.tcc.retry")
                .tag("phase", "confirm").tag("outcome", "ok").counter().count());
        assertEquals(1.0, registry.get("crm.tx.tcc.retry")
                .tag("phase", "confirm").tag("outcome", "skip").counter().count());
    }

    @Test
    void noRegistry_isNoop() {
        TccMetrics metrics = new TccMetrics((io.micrometer.core.instrument.MeterRegistry) null);

        metrics.scan("due", 1);
        metrics.retry("cancel", "ok");
    }
}
