package io.microsphere.labs.cloud.web;

import io.microsphere.labs.cloud.client.DemoServiceClient;
import io.microsphere.spring.cloud.client.discovery.UnionDiscoveryClient;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用法展示端点。
 *
 * <p>不要求真实运行；启动后可用于观察 Union Discovery 与 Feign 接线。</p>
 */
@RestController
@RequestMapping("/lab")
public class LabController {

    private final UnionDiscoveryClient unionDiscoveryClient;

    private final DemoServiceClient demoServiceClient;

    public LabController(UnionDiscoveryClient unionDiscoveryClient, DemoServiceClient demoServiceClient) {
        this.unionDiscoveryClient = unionDiscoveryClient;
        this.demoServiceClient = demoServiceClient;
    }

    /**
     * 聚合后的服务列表：应包含两个注册中心的并集。
     */
    @GetMapping("/services")
    public List<String> services() {
        return unionDiscoveryClient.getServices();
    }

    /**
     * 聚合后的实例列表：同名服务来自多个注册中心的实例都会出现。
     */
    @GetMapping("/instances/{serviceId}")
    public List<ServiceInstance> instances(@PathVariable String serviceId) {
        return unionDiscoveryClient.getInstances(serviceId);
    }

    /**
     * Feign 调用入口；展示 {@code @EnableFeignAutoRefresh} 的客户端。
     */
    @GetMapping("/hello")
    public String hello() {
        return demoServiceClient.hello();
    }
}
