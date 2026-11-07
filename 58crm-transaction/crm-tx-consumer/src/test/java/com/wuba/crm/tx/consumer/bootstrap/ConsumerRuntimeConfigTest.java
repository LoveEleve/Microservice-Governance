package com.wuba.crm.tx.consumer.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsumerRuntimeConfigTest {

    @Test
    void defaults_buildStreamAndGroup() {
        ConsumerRuntimeConfig config = ConsumerRuntimeConfig.defaults("lead", "consumer-1");

        assertEquals("tx:stream:lead", config.stream());
        assertEquals("tx-group-lead", config.group());
    }

    @Test
    void zeroQueueCapacity_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ConsumerRuntimeConfig(
                "lead", "c-1", "tx:stream:", "tx-group-", 0, 500,
                java.time.Duration.ofMillis(100), 200, 8,
                java.time.Duration.ofSeconds(60), java.time.Duration.ofMinutes(5),
                java.time.Duration.ofSeconds(30), 3, java.time.Duration.ofMillis(10),
                java.time.Duration.ofSeconds(20), 200, 100_000,
                java.time.Duration.ofMillis(100), 100, java.time.Duration.ofSeconds(30),
                500, java.time.Duration.ofSeconds(1)));
    }
}
