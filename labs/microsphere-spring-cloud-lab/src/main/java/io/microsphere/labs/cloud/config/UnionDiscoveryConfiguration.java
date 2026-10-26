package io.microsphere.labs.cloud.config;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Union Discovery 展示。
 *
 * <p>两个 {@link DiscoveryClient} 代表两个注册中心的读侧；
 * 打开 {@code microsphere.spring.cloud.client.discovery.mode=union} 后，
 * Microsphere 的 {@code UnionDiscoveryClient} 会把它们的服务/实例聚合起来。</p>
 */
@Configuration(proxyBeanMethods = false)
public class UnionDiscoveryConfiguration {

    @Bean
    public DiscoveryClient discoveryClientA() {
        return new StaticDiscoveryClient("registry-a", List.of(
                instance("demo-a-1", "demo-service", 8081, Map.of("zone", "zone-a")),
                instance("order-a-1", "order-service", 8091, Map.of("zone", "zone-a"))
        ));
    }

    @Bean
    public DiscoveryClient discoveryClientB() {
        return new StaticDiscoveryClient("registry-b", List.of(
                instance("demo-b-1", "demo-service", 8082, Map.of("zone", "zone-b")),
                instance("user-b-1", "user-service", 8092, Map.of("zone", "zone-b"))
        ));
    }

    private static ServiceInstance instance(String instanceId, String serviceId, int port, Map<String, String> metadata) {
        return new DefaultServiceInstance(instanceId, serviceId, "127.0.0.1", port, false, metadata);
    }

    /**
     * 内存 DiscoveryClient：仅用于展示 UnionDiscoveryClient 的聚合行为。
     */
    static final class StaticDiscoveryClient implements DiscoveryClient {

        private final String description;

        private final List<ServiceInstance> instances;

        StaticDiscoveryClient(String description, List<ServiceInstance> instances) {
            this.description = description;
            this.instances = instances;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceId) {
            return instances.stream()
                    .filter(instance -> Objects.equals(serviceId, instance.getServiceId()))
                    .toList();
        }

        @Override
        public List<String> getServices() {
            return instances.stream()
                    .map(ServiceInstance::getServiceId)
                    .distinct()
                    .toList();
        }
    }
}
