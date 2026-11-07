package com.wuba.crm.tx.starter;

import com.wuba.crm.tx.consumer.bootstrap.ConsumerBootstrap;
import com.wuba.crm.tx.consumer.bootstrap.ConsumerBootstrapManager;
import com.wuba.crm.tx.consumer.persistence.ConsumerRecordMapper;
import com.wuba.crm.tx.consumer.persistence.ConsumerRecordRepository;
import com.wuba.crm.tx.consumer.persistence.IdempotentMapper;
import com.wuba.crm.tx.consumer.persistence.IdempotentRepository;
import com.wuba.crm.tx.consumer.persistence.MybatisConsumerRecordRepository;
import com.wuba.crm.tx.consumer.persistence.MybatisIdempotentRepository;
import com.wuba.crm.tx.consumer.worker.HandlerRegistry;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@AutoConfiguration
@ConditionalOnClass(ConsumerBootstrap.class)
@EnableConfigurationProperties(CrmTxProperties.class)
@ConditionalOnProperty(prefix = "crm.tx.consumer", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CrmTxConsumerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SqlSessionTemplate.class)
    public ConsumerRecordMapper crmTxConsumerRecordMapper(SqlSessionTemplate template) {
        return template.getMapper(ConsumerRecordMapper.class);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SqlSessionTemplate.class)
    public IdempotentMapper crmTxIdempotentMapper(SqlSessionTemplate template) {
        return template.getMapper(IdempotentMapper.class);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ConsumerRecordMapper.class)
    public ConsumerRecordRepository crmTxConsumerRecordRepository(ConsumerRecordMapper mapper) {
        return new MybatisConsumerRecordRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(IdempotentMapper.class)
    public IdempotentRepository crmTxIdempotentRepository(IdempotentMapper mapper) {
        return new MybatisIdempotentRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public HandlerRegistry crmTxHandlerRegistry() {
        return new HandlerRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(PlatformTransactionManager.class)
    public TransactionOperations crmTxTransactionOperations(PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnBean({
            ConsumerRecordRepository.class,
            IdempotentRepository.class,
            HandlerRegistry.class,
            TransactionOperations.class,
            StringRedisTemplate.class
    })
    public ConsumerBootstrapManager crmTxConsumerBootstrapManager(
            CrmTxProperties properties,
            ConsumerRecordRepository repository,
            IdempotentRepository idempotent,
            HandlerRegistry handlers,
            TransactionOperations transactions,
            StringRedisTemplate redisTemplate) {
        List<ConsumerBootstrap> bootstraps = new ArrayList<>();
        String consumerId = properties.getConsumer().getConsumerIdPrefix()
                + "-" + UUID.randomUUID();
        for (String bizCode : properties.getBizCodes()) {
            bootstraps.add(new ConsumerBootstrap(
                    properties.toConsumerConfig(bizCode, consumerId),
                    repository,
                    idempotent,
                    handlers,
                    transactions,
                    redisTemplate));
        }
        ConsumerBootstrapManager manager = new ConsumerBootstrapManager(bootstraps);
        manager.start();
        return manager;
    }
}
