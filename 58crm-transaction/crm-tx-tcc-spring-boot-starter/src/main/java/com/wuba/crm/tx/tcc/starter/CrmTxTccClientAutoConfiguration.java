package com.wuba.crm.tx.tcc.starter;

import com.wuba.crm.tx.tcc.api.TccTransactionTemplate;
import com.wuba.crm.tx.tcc.client.DefaultTccTransactionTemplate;
import com.wuba.crm.tx.tcc.client.TccCoordinatorEndpointProvider;
import com.wuba.crm.tx.tcc.client.TccTransactionManager;
import com.wuba.crm.tx.tcc.client.internal.CoordinatorGrpcClient;
import com.wuba.crm.tx.tcc.client.internal.TccCoordinatorOperations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;

/**
 * TCC TM 客户端自动装配。
 */
@AutoConfiguration
@ConditionalOnClass(TccTransactionTemplate.class)
@EnableConfigurationProperties(CrmTxTccProperties.class)
@ConditionalOnProperty(
        prefix = "crm.tx.tcc.client",
        name = "enabled",
        havingValue = "true")
public class CrmTxTccClientAutoConfiguration {

    /**
     * Coordinator endpoint provider。
     *
     * @param discoveryClientProvider discovery
     * @param properties              配置
     * @return provider
     */
    @Bean
    @ConditionalOnMissingBean
    public TccCoordinatorEndpointProvider tccCoordinatorEndpointProvider(
            ObjectProvider<DiscoveryClient> discoveryClientProvider,
            CrmTxTccProperties properties) {
        DiscoveryClient discoveryClient = discoveryClientProvider.getIfAvailable();
        if (discoveryClient != null) {
            return new DiscoveryTccCoordinatorEndpointProvider(
                    discoveryClient, properties.getClient().getCoordinatorServiceName());
        }
        String address = properties.getClient().getCoordinatorAddress();
        if (address == null || address.isBlank()) {
            throw new IllegalStateException(
                    "no DiscoveryClient and crm.tx.tcc.client.coordinator-address is blank");
        }
        return () -> address;
    }

    /**
     * 公共 TCC 模板。
     *
     * @param endpointProvider endpoint provider
     * @param properties       配置
     * @return template
     */
    @Bean
    @ConditionalOnMissingBean
    public TccCoordinatorOperations tccCoordinatorOperations(
            TccCoordinatorEndpointProvider endpointProvider,
            CrmTxTccProperties properties) {
        return new CoordinatorGrpcClient(
                endpointProvider, properties.getClient().getDeadlineSeconds());
    }

    @Bean
    @ConditionalOnMissingBean
    public TccTransactionTemplate tccTransactionTemplate(
            TccCoordinatorOperations operations) {
        return new DefaultTccTransactionTemplate(operations);
    }

    @Bean
    @ConditionalOnMissingBean
    public TccTransactionManager tccTransactionManager(
            TccCoordinatorOperations operations) {
        return new TccTransactionManager(operations);
    }
}
