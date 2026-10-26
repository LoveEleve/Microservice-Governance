# Microsphere Spring Cloud Lab

展示 `microsphere-spring-cloud` 的四个服务治理扩展点，**不依赖真实注册中心即可阅读代码**。

## 代码地图

| 文件 | 展示点 |
|---|---|
| `MicrosphereSpringCloudLabApplication` | `@EnableFeignClients` + `@EnableFeignAutoRefresh` |
| `config/MultipleRegistryConfiguration` | 两个**不同 Registration 类型**的注册中心 A/B（内部用 `SimpleServiceRegistry` 作内存 delegate），由 Microsphere 自动装配成 `MultipleServiceRegistry` / `MultipleRegistration` |
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

## 与官方实现对照（评审用）

| 能力 | 官方 Spring Cloud | Microsphere | 本 Lab |
|---|---|---|---|
| 一实例多注册 | 默认单个 `ServiceRegistry`，无多注册编排 | `MultipleServiceRegistry` 按 `Registration` 类型委托 | A/B 两个 Registration 子类型 + 两个 registry |
| 发现聚合 | `CompositeDiscoveryClient` 取**第一个非空** client 结果 | `UnionDiscoveryClient` 对**所有** client 做并集 | `demo-service` 在 A/B 都有实例，能看出两种语义差异 |
| 实例去重 | Composite 不跨 client 去重 | Union 直接拼接，不保证去重 | 同名实例会重复，这是待评审点 |
| 失败语义 | 单 registry，边界清楚 | 逐个委托，中途失败可能部分注册 | 不能假设多注册原子；生产要幂等/对账 |
| 注册事件 | 无细粒度注册生命周期事件 | Pre/Registered/PreDeregistered/Deregistered | 本 Lab 只打日志，验证事件接线 |
| Feign 刷新 | 配置刷新不重建 Feign 组件 | `@EnableFeignAutoRefresh` 监听变更重建 | 本 Lab 展示注解接线，不验证并发行为 |

> 结论：Microsphere 补的是官方空白，不是“官方正确答案”。使用前必须按 `docs/microsphere/00-overview/技术评审方法.md` 检查语义、失败和边界。

## 运行边界

- 本工程**不需要** Nacos/Eureka/Consul/Zookeeper；
- `SimpleServiceRegistry` 只是内存替身，用来展示多注册中心委托链；
- `/lab/hello` 需要 `demo.service.url` 指向真实服务，默认 `127.0.0.1:8081`，仅作接线示例；
- 真实生产接入时，把 `SimpleServiceRegistry` 换成对应的 `NacosServiceRegistry`/`EurekaServiceRegistry` 等，`MultipleServiceRegistry` 会自动按注册类型委托。
