package io.microsphere.labs.cloud.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * OpenFeign 动态刷新展示。
 *
 * <p>应用启动类开启 {@code @EnableFeignAutoRefresh} 后，修改
 * {@code spring.cloud.openfeign.client.config.demo-service.*} 会刷新该客户端的
 * Encoder/Decoder/Retryer/RequestInterceptor 等组件，不需要重启。</p>
 *
 * <p>这里固定 {@code url}，避免展示工程依赖真实注册中心；生产中可以去掉 url 走
 * {@code lb://demo-service}。</p>
 */
@FeignClient(name = "demo-service", url = "${demo.service.url:http://127.0.0.1:8081}")
public interface DemoServiceClient {

    @GetMapping("/hello")
    String hello();
}
