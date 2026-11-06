package com.wuba.crm.tx.starter;

import com.wuba.crm.tx.outbox.api.OutboxPublisher;
import com.wuba.crm.tx.outbox.config.BizCodeRegistry;
import com.wuba.crm.tx.outbox.persistence.LocalMessageMapper;
import com.wuba.crm.tx.outbox.producer.OutboxConfig;
import com.wuba.crm.tx.outbox.producer.OutboxProducer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Starter 的自动装配和业务方最小依赖。
 */
class CrmTxAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CrmTxAutoConfiguration.class))
            .withUserConfiguration(TestBeans.class)
            .withPropertyValues(
                    "crm.tx.biz-codes=lead,quota",
                    "crm.tx.auto-start=false");

    @Test
    void autoConfiguresPublisherAndProducerWithoutBusinessWiring() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(BizCodeRegistry.class);
            assertThat(context).hasSingleBean(OutboxConfig.class);
            assertThat(context).hasSingleBean(OutboxPublisher.class);
            assertThat(context).hasSingleBean(OutboxProducer.class);
        });
    }

    @Test
    void disabledPropertyStopsAutoConfiguration() {
        runner.withPropertyValues("crm.tx.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(OutboxPublisher.class));
    }

    @Configuration
    static class TestBeans {

        @Bean
        LocalMessageMapper localMessageMapper() {
            return Mockito.mock(LocalMessageMapper.class);
        }

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return Mockito.mock(StringRedisTemplate.class);
        }
    }
}
