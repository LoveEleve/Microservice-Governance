package com.wuba.crm.tx.demo;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务方配置文件必须能绑定为 Registry + OutboxConfig。
 */
class DemoConfigLoaderTest {

    @Test
    void loadsApplicationYaml() throws Exception {
        DemoProperties properties = DemoConfigLoader.load(
                new ClassPathResource("application.yml"));

        assertEquals("tx:stream:", properties.getStreamPrefix());
        assertEquals(2, properties.getBizCodes().size());
        assertTrue(properties.toRegistry().contains("lead"));
        assertTrue(properties.toRegistry().contains("quota"));

        assertEquals(Duration.ofMillis(10), properties.getOutbox().getLinger());
        assertEquals(256, properties.getOutbox().getBatchSize());
        assertEquals(8, properties.toOutboxConfig().senderThreads());
    }
}
