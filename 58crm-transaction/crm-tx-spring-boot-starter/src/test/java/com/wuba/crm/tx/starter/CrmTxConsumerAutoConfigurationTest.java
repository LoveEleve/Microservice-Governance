package com.wuba.crm.tx.starter;

import com.wuba.crm.tx.consumer.bootstrap.ConsumerBootstrapManager;
import com.wuba.crm.tx.consumer.persistence.ConsumerRecordMapper;
import com.wuba.crm.tx.consumer.persistence.IdempotentMapper;
import com.wuba.crm.tx.consumer.persistence.IdempotentRepository;
import com.wuba.crm.tx.consumer.worker.HandlerRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

class CrmTxConsumerAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CrmTxConsumerAutoConfiguration.class))
            .withUserConfiguration(TestBeans.class)
            .withPropertyValues("crm.tx.biz-codes=");

    @Test
    void autoConfiguresConsumerInfrastructure() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(HandlerRegistry.class);
            assertThat(context).hasSingleBean(IdempotentRepository.class);
            assertThat(context).hasSingleBean(ConsumerBootstrapManager.class);
            assertThat(context.getBean(ConsumerBootstrapManager.class).size()).isZero();
        });
    }

    @Test
    void consumerDisabledStopsAutoConfiguration() {
        runner.withPropertyValues("crm.tx.consumer.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(ConsumerBootstrapManager.class));
    }

    @Configuration
    static class TestBeans {

        @Bean
        ConsumerRecordMapper consumerRecordMapper() {
            return Mockito.mock(ConsumerRecordMapper.class);
        }

        @Bean
        IdempotentMapper idempotentMapper() {
            return Mockito.mock(IdempotentMapper.class);
        }

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return Mockito.mock(StringRedisTemplate.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return Mockito.mock(PlatformTransactionManager.class);
        }
    }
}
