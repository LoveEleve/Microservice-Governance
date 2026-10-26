package io.microsphere.labs.cloud.config;

import io.microsphere.spring.cloud.client.service.registry.DefaultRegistration;
import io.microsphere.spring.cloud.client.service.registry.SimpleServiceRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;

/**
 * 多注册中心展示。
 *
 * <p>两个 {@link SimpleServiceRegistry} 代表两个注册中心（生产中可替换为 Nacos/Eureka/Consul/Zookeeper）。
 * 打开 {@code microsphere.spring.cloud.multiple-registration.enabled=true} 后，
 * Microsphere 的 {@code ServiceRegistryAutoConfiguration} 会创建：</p>
 *
 * <ul>
 *     <li>{@code MultipleRegistration}：聚合所有 {@code Registration}；</li>
 *     <li>{@code MultipleServiceRegistry}：按注册中心委托 register/deregister/setStatus；</li>
 *     <li>{@code MultipleAutoServiceRegistration}：统一自动注册。</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class MultipleRegistryConfiguration {

    /**
     * 本实例的注册信息。生产中通常由 Nacos/Eureka 的 Registration 实现提供。
     */
    @Bean
    public DefaultRegistration demoRegistration() {
        DefaultRegistration registration = new DefaultRegistration();
        registration.setInstanceId("microsphere-spring-cloud-lab-1");
        registration.setServiceId("microsphere-spring-cloud-lab");
        registration.setHost("127.0.0.1");
        registration.setPort(8080);
        registration.setSecure(false);
        registration.getMetadata().put("zone", "zone-a");
        return registration;
    }

    /**
     * 注册中心 A 的本地替身。
     */
    @Bean
    public SimpleServiceRegistry registryA() {
        return new SimpleServiceRegistry(new LinkedHashMap<>());
    }

    /**
     * 注册中心 B 的本地替身。
     */
    @Bean
    public SimpleServiceRegistry registryB() {
        return new SimpleServiceRegistry(new LinkedHashMap<>());
    }
}
