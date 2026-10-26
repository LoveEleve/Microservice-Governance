package io.microsphere.labs.cloud.config;

import io.microsphere.spring.cloud.client.service.registry.DefaultRegistration;
import io.microsphere.spring.cloud.client.service.registry.SimpleServiceRegistry;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;

/**
 * 多注册中心展示。
 *
 * <p>两个注册中心分别使用自己的 {@link org.springframework.cloud.client.serviceregistry.Registration}
 * 子类型：{@link RegistryARegistration} / {@link RegistryBRegistration}。
 * 这是 {@code MultipleServiceRegistry} 的关键前提——它按 {@code Registration} 类型把
 * 同一个逻辑实例分发给对应注册中心；如果两个注册中心使用完全相同的 Registration 类型，
 * 无法区分“这个注册中心该拿哪一份 Registration”。</p>
 *
 * <p>打开 {@code microsphere.spring.cloud.multiple-registration.enabled=true} 后，
 * Microsphere 的 {@code ServiceRegistryAutoConfiguration} 会创建
 * {@code MultipleRegistration} / {@code MultipleServiceRegistry} / {@code MultipleAutoServiceRegistration}。</p>
 */
@Configuration(proxyBeanMethods = false)
public class MultipleRegistryConfiguration {

    @Bean
    public RegistryARegistration registryARegistration() {
        return registration(new RegistryARegistration(), "registry-a-1", 8081);
    }

    @Bean
    public RegistryBRegistration registryBRegistration() {
        return registration(new RegistryBRegistration(), "registry-b-1", 8082);
    }

    @Bean
    public RegistryAServiceRegistry registryAServiceRegistry() {
        return new RegistryAServiceRegistry();
    }

    @Bean
    public RegistryBServiceRegistry registryBServiceRegistry() {
        return new RegistryBServiceRegistry();
    }

    private static <R extends DefaultRegistration> R registration(R registration,
                                                                  String instanceId,
                                                                  int port) {
        registration.setInstanceId(instanceId);
        registration.setServiceId("microsphere-spring-cloud-lab");
        registration.setHost("127.0.0.1");
        registration.setPort(port);
        registration.setSecure(false);
        registration.getMetadata().put("zone", port == 8081 ? "zone-a" : "zone-b");
        return registration;
    }

    /**
     * A 注册中心的 Registration 类型。
     */
    public static class RegistryARegistration extends DefaultRegistration {
    }

    /**
     * B 注册中心的 Registration 类型。
     */
    public static class RegistryBRegistration extends DefaultRegistration {
    }

    /**
     * 用 SimpleServiceRegistry 作为内存 delegate，避免依赖真实注册中心。
     */
    public abstract static class AbstractInMemoryServiceRegistry<R extends DefaultRegistration>
            implements ServiceRegistry<R> {

        private final SimpleServiceRegistry delegate = new SimpleServiceRegistry(new LinkedHashMap<>());

        @Override
        public void register(R registration) {
            delegate.register(registration);
        }

        @Override
        public void deregister(R registration) {
            delegate.deregister(registration);
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public void setStatus(R registration, String status) {
            delegate.setStatus(registration, status);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getStatus(R registration) {
            return (T) delegate.getStatus(registration);
        }
    }

    /**
     * A 注册中心。
     */
    public static class RegistryAServiceRegistry extends AbstractInMemoryServiceRegistry<RegistryARegistration> {
    }

    /**
     * B 注册中心。
     */
    public static class RegistryBServiceRegistry extends AbstractInMemoryServiceRegistry<RegistryBRegistration> {
    }
}
