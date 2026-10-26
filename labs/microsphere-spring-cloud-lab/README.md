# Microsphere Spring Cloud Lab

展示 `microsphere-spring-cloud` 的四个服务治理扩展点，**不依赖真实注册中心即可阅读代码**。

## 代码地图

| 文件 | 展示点 |
|---|---|
| `MicrosphereSpringCloudLabApplication` | `@EnableFeignClients` + `@EnableFeignAutoRefresh` |
| `config/MultipleRegistryConfiguration` | 两个 `SimpleServiceRegistry` + 一个 `Registration`，由 Microsphere 自动装配成 `MultipleServiceRegistry` / `MultipleRegistration` |
| `config/UnionDiscoveryConfiguration` | 两个内存 `DiscoveryClient`，由 `UnionDiscoveryClient` 聚合 |
| `event/RegistrationEventListener` | 注册/注销生命周期事件 |
| `client/DemoServiceClient` | OpenFeign 客户端，配置变更可动态刷新 |
| `web/LabController` | `/lab/services`、`/lab/instances/{serviceId}`、`/lab/hello` |
| `application.yml` | 多注册中心与 Union Discovery 开关 |

## 关键配置

```yaml
microsphere:
  spring:
    cloud:
      multiple-registration:
        enabled: true
      client:
        discovery:
          mode: union
```

## 运行边界

- 本工程**不需要** Nacos/Eureka/Consul/Zookeeper；
- `SimpleServiceRegistry` 只是内存替身，用来展示多注册中心委托链；
- `/lab/hello` 需要 `demo.service.url` 指向真实服务，默认 `127.0.0.1:8081`，仅作接线示例；
- 真实生产接入时，把 `SimpleServiceRegistry` 换成对应的 `NacosServiceRegistry`/`EurekaServiceRegistry` 等，`MultipleServiceRegistry` 会自动按注册类型委托。
