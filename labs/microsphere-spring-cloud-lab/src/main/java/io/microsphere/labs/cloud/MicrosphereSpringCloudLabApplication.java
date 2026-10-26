package io.microsphere.labs.cloud;

import io.microsphere.labs.cloud.client.DemoServiceClient;
import io.microsphere.spring.cloud.openfeign.autorefresh.EnableFeignAutoRefresh;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Microsphere Spring Cloud 使用展示入口。
 *
 * <p>本 Lab 只展示 API 用法，不依赖真实注册中心/Nacos 运行：</p>
 * <ul>
 *     <li>多注册中心：两个 {@code SimpleServiceRegistry} 交给 {@code MultipleServiceRegistry} 委托；</li>
 *     <li>Union Discovery：两个内存 {@code DiscoveryClient} 由 {@code UnionDiscoveryClient} 聚合；</li>
 *     <li>注册事件：监听 {@code Registration*Event}；</li>
 *     <li>OpenFeign 动态刷新：{@code @EnableFeignAutoRefresh}。</li>
 * </ul>
 */
@SpringBootApplication
@EnableFeignClients(basePackageClasses = DemoServiceClient.class)
@EnableFeignAutoRefresh
public class MicrosphereSpringCloudLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(MicrosphereSpringCloudLabApplication.class, args);
    }
}
