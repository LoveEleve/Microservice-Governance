package com.wuba.crm.tx.e2e.lead;

import com.wuba.crm.tx.tcc.api.TccTransactionTemplate;
import com.wuba.crm.tx.tcc.client.CoordinatorGrpcClient;
import com.wuba.crm.tx.tcc.client.DefaultTccTransactionTemplate;
import com.wuba.crm.tx.tcc.client.TccCoordinatorEndpointProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 业务方只依赖公共 TCC 模板；gRPC/proto 全部封在 client 内。
 */
@Configuration
public class TccClientConfiguration {

    /**
     * Coordinator 地址提供者。
     *
     * @param discoveryClient Nacos discovery
     * @param serviceName Coordinator 服务名
     * @return endpoint provider
     */
    @Bean
    public TccCoordinatorEndpointProvider coordinatorEndpointProvider(
            DiscoveryClient discoveryClient,
            @Value("${crm.tx.tcc.coordinator.service-name:crm-tx-tcc-coordinator}")
            String serviceName) {
        return () -> {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
            if (instances == null || instances.isEmpty()) {
                throw new IllegalStateException("no coordinator instance: " + serviceName);
            }
            ServiceInstance instance = instances.get(0);
            return instance.getHost() + ":" + instance.getPort();
        };
    }

    /**
     * 公共 TCC 模板。
     *
     * @param endpointProvider endpoint provider
     * @param deadlineSeconds 超时秒数
     * @return template
     */
    @Bean
    public TccTransactionTemplate tccTransactionTemplate(
            TccCoordinatorEndpointProvider endpointProvider,
            @Value("${crm.tx.tcc.coordinator.deadline-seconds:15}")
            long deadlineSeconds) {
        return new DefaultTccTransactionTemplate(
                new CoordinatorGrpcClient(endpointProvider, deadlineSeconds));
    }
}
