package com.wuba.crm.tx.tcc.coordinator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * TCC Coordinator 指标。
 *
 * <p>没有 Micrometer registry 时自动降级为 no-op；不会阻塞核心流程。</p>
 */
@Component
public class TccMetrics {

    private final MeterRegistry registry;

    /**
     * Spring 注入构造器。
     *
     * @param registries MeterRegistry provider
     */
    @Autowired
    public TccMetrics(ObjectProvider<MeterRegistry> registries) {
        this(registries == null ? null : registries.getIfAvailable());
    }

    /**
     * 测试/无指标构造器。
     *
     * @param registry MeterRegistry，可为 null
     */
    public TccMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 记录一次扫描发现。
     *
     * @param kind  due / timeout
     * @param count 数量
     */
    public void scan(String kind, int count) {
        if (count <= 0) {
            return;
        }
        increment("crm.tx.tcc.scan", count, "kind", kind);
    }

    /**
     * 记录一次重试处理结果。
     *
     * @param phase   confirm / cancel
     * @param outcome ok / skip / error
     */
    public void retry(String phase, String outcome) {
        increment("crm.tx.tcc.retry", 1, "phase", phase, "outcome", outcome);
    }

    private void increment(String name, double amount, String... tags) {
        if (registry == null) {
            return;
        }
        Counter.Builder builder = Counter.builder(name);
        for (int i = 0; i + 1 < tags.length; i += 2) {
            builder.tag(tags[i], tags[i + 1]);
        }
        builder.register(registry).increment(amount);
    }
}
