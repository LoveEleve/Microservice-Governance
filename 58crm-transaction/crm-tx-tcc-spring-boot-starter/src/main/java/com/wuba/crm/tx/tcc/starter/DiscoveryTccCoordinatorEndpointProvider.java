package com.wuba.crm.tx.tcc.starter;

import com.wuba.crm.tx.tcc.client.TccCoordinatorEndpointProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;

/**
 * 从 Spring Cloud Discovery 获取 Coordinator gRPC 实例。
 */
public class DiscoveryTccCoordinatorEndpointProvider
        implements TccCoordinatorEndpointProvider {

    private final DiscoveryClient discoveryClient;
    private final String serviceName;

    /**
     * 创建 provider。
     *
     * @param discoveryClient discovery client
     * @param serviceName     Coordinator 服务名
     */
    public DiscoveryTccCoordinatorEndpointProvider(
            DiscoveryClient discoveryClient,
            String serviceName) {
        this.discoveryClient = discoveryClient;
        this.serviceName = serviceName;
    }

    /**
     * 选择第一个实例。
     *
     * @return host:port
     */
    @Override
    public String target() {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (instances == null || instances.isEmpty()) {
            throw new IllegalStateException("no coordinator instance: " + serviceName);
        }
        ServiceInstance instance = instances.get(0);
        return instance.getHost() + ":" + instance.getPort();
    }
}
