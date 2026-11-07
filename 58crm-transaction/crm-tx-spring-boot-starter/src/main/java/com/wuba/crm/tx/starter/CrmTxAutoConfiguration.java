package com.wuba.crm.tx.starter;

import com.wuba.crm.tx.outbox.api.OutboxPublisher;
import com.wuba.crm.tx.outbox.config.BizCodeRegistry;
import com.wuba.crm.tx.outbox.persistence.LocalMessageMapper;
import com.wuba.crm.tx.outbox.persistence.LocalMessageRepository;
import com.wuba.crm.tx.outbox.persistence.MybatisLocalMessageRepository;
import com.wuba.crm.tx.outbox.persistence.MybatisOutboxPublisher;
import com.wuba.crm.tx.outbox.pipeline.interceptor.OutboxInterceptor;
import com.wuba.crm.tx.outbox.pipeline.interceptor.OutboxInterceptorChain;
import com.wuba.crm.tx.outbox.pipeline.route.BizCodePartitioner;
import com.wuba.crm.tx.outbox.pipeline.route.OutboxPartitioner;
import com.wuba.crm.tx.outbox.pipeline.serializer.DefaultOutboxSerializer;
import com.wuba.crm.tx.outbox.pipeline.serializer.OutboxSerializer;
import com.wuba.crm.tx.outbox.pipeline.stream.RedisStreamPublisher;
import com.wuba.crm.tx.outbox.pipeline.stream.RedisStreamPublisherAdapter;
import com.wuba.crm.tx.outbox.producer.OutboxBuffer;
import com.wuba.crm.tx.outbox.producer.OutboxConfig;
import com.wuba.crm.tx.outbox.producer.OutboxInFlight;
import com.wuba.crm.tx.outbox.producer.OutboxProducer;
import com.wuba.crm.tx.outbox.producer.OutboxProducerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * Starter 自动装配。
 *
 * <p>业务方只需要提供：
 * <ul>
 *   <li>{@link LocalMessageMapper}</li>
 *   <li>{@link StringRedisTemplate}</li>
 * </ul>
 * 再配置 {@code crm.tx.*}，就能注入 {@link OutboxPublisher}。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(CrmTxProperties.class)
@ConditionalOnProperty(prefix = "crm.tx", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CrmTxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BizCodeRegistry crmTxBizCodeRegistry(CrmTxProperties properties) {
        return properties.toRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxConfig crmTxOutboxConfig(CrmTxProperties properties) {
        return properties.toOutboxConfig();
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxBuffer crmTxOutboxBuffer(OutboxConfig config) {
        return new OutboxBuffer(
                config.bufferCapacity(),
                config.bufferCapacityBytes(),
                config.perBizCapacity(),
                config.perBizCapacityBytes());
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxInFlight crmTxOutboxInFlight() {
        return new OutboxInFlight();
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxInterceptorChain crmTxInterceptorChain(List<OutboxInterceptor> interceptors) {
        return new OutboxInterceptorChain(interceptors);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxPartitioner crmTxPartitioner() {
        return new BizCodePartitioner();
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxSerializer crmTxSerializer() {
        return new DefaultOutboxSerializer();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(LocalMessageMapper.class)
    public LocalMessageRepository crmTxLocalMessageRepository(LocalMessageMapper mapper) {
        return new MybatisLocalMessageRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(OutboxPublisher.class)
    @ConditionalOnBean({LocalMessageMapper.class, BizCodeRegistry.class})
    public MybatisOutboxPublisher crmTxOutboxPublisher(
            LocalMessageMapper mapper,
            BizCodeRegistry registry) {
        return new MybatisOutboxPublisher(mapper, registry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(StringRedisTemplate.class)
    public RedisStreamPublisher crmTxRedisStreamPublisher(StringRedisTemplate redisTemplate) {
        return new RedisStreamPublisherAdapter(redisTemplate);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnBean({RedisStreamPublisher.class, LocalMessageRepository.class})
    @ConditionalOnProperty(prefix = "crm.tx", name = "auto-start",
            havingValue = "true", matchIfMissing = true)
    public OutboxProducer crmTxOutboxProducerAutoStart(
            OutboxBuffer buffer,
            RedisStreamPublisher streamPublisher,
            OutboxInterceptorChain interceptors,
            OutboxPartitioner partitioner,
            OutboxSerializer serializer,
            LocalMessageRepository repository,
            OutboxInFlight inFlight,
            OutboxConfig config,
            BizCodeRegistry registry) {
        OutboxProducer producer = OutboxProducerFactory.create(
                buffer, streamPublisher, interceptors, partitioner, serializer,
                repository, inFlight, config, registry);
        producer.start();
        return producer;
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnBean({RedisStreamPublisher.class, LocalMessageRepository.class})
    @ConditionalOnProperty(prefix = "crm.tx", name = "auto-start",
            havingValue = "false")
    public OutboxProducer crmTxOutboxProducerManualStart(
            OutboxBuffer buffer,
            RedisStreamPublisher streamPublisher,
            OutboxInterceptorChain interceptors,
            OutboxPartitioner partitioner,
            OutboxSerializer serializer,
            LocalMessageRepository repository,
            OutboxInFlight inFlight,
            OutboxConfig config,
            BizCodeRegistry registry) {
        return OutboxProducerFactory.create(
                buffer, streamPublisher, interceptors, partitioner, serializer,
                repository, inFlight, config, registry);
    }
}
