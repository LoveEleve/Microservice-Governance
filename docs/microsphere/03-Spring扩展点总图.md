# 03 · Spring 扩展点总图

> **口径**：Microsphere 的每个仓库，本质都是“在 Spring/Spring Boot/Spring Cloud 的某个扩展点上做增强”。读源码前，先认清它挂在哪。
> **一句话**：不懂扩展点的执行顺序，就会把“为什么没生效”误判成“代码写错了”。
> **系列**：42 篇，见 [00-系列规划](00-系列规划.md)；源码地图见 [01 源码地图与阅读方法](01-源码地图与阅读方法.md)。

---

## 0. 先看一次启动：扩展点按什么顺序执行

```
SpringApplication.run()
  → EnvironmentPostProcessor          （Environment 就绪前）
  → ApplicationContextInitializer     （上下文刷新前）
  → BeanDefinitionRegistryPostProcessor（Bean 定义注册）
  → BeanFactoryPostProcessor          （Bean 定义修改）
  → BeanPostProcessor                 （Bean 实例化前后）
  → SmartInitializingSingleton        （单例全部实例化后）
  → ContextRefreshedEvent             （上下文刷新完成）
  → ApplicationRunner / CommandLineRunner
```

Microsphere 的很多“没生效”问题，根因不是类写错，而是**挂在了错误的阶段**。

---

## 1. 扩展点分层

### 1.1 启动/Environment

| 扩展点 | 时机 | Microsphere 用法 |
|---|---|---|
| `EnvironmentPostProcessor` | Environment 构建后、上下文刷新前 | 默认属性、配置源、环境适配 |
| `PropertySource` / `PropertySourceLoader` | 配置加载 | `microsphere-configuration` 的 Nacos/Etcd/ZK Loader |
| `ApplicationContextInitializer` | 上下文 refresh 前 | 早期上下文定制 |
| `SpringApplicationRunListener` | 启动全过程 | 启动生命周期观测 |

**关键点**：`EnvironmentPostProcessor` 里不能依赖普通 Bean；它跑在 Bean 容器之前。

---

### 1.2 Bean 定义阶段

| 扩展点 | 时机 | Microsphere 用法 |
|---|---|---|
| `ImportSelector` | 配置类导入 | `@EnableFeignAutoRefresh` 导入 Marker/AutoConfiguration |
| `ImportBeanDefinitionRegistrar` | 动态注册 Bean 定义 | `@EnableMyBatis`、Resilience4j 注解 |
| `BeanDefinitionRegistryPostProcessor` | Bean 定义注册后、实例化前 | Gateway 的 `FilteringWebHandlerBeanDefinitionRegistryPostProcessor` |
| `AutoConfigurationImportFilter` | 自动配置过滤 | `ConfigurableAutoConfigurationImportFilter` |
| `BeanFactoryPostProcessor` | Bean 定义修改 | 属性占位符/Bean 定义调整 |
| `@Conditional*` | 条件判断 | `@ConditionalOnMissingBean`、`@ConditionalOnProperty`、自定义条件 |

**关键点**：

- `@ConditionalOnMissingBean` 依赖**Bean 定义注册顺序**；自动配置顺序错了，条件会误判；
- `BeanDefinitionRegistryPostProcessor` 比普通 `BeanPostProcessor` 更早，不能依赖普通 Bean。

---

### 1.3 Bean 实例化/代理阶段

| 扩展点 | 时机 | Microsphere 用法 |
|---|---|---|
| `BeanPostProcessor` | Bean 初始化前后 | Redis 连接工厂代理、Druid DataSource 包装 |
| `InstantiationAwareBeanPostProcessor` | 实例化前后 | 代理/替换 Bean |
| `SmartInitializingSingleton` | 所有单例实例化后 | `UnionDiscoveryClient` 收集 DiscoveryClient |
| `InitializingBean` / `DisposableBean` | 初始化/销毁 | 资源生命周期 |
| `FactoryBean` | 复杂对象创建 | 客户端/代理对象 |

**关键点**：

- BPP 返回代理对象时，注意 `@Transactional`/AOP 的顺序；
- BPP 内部不要过早触发 Bean 初始化，容易循环依赖/提前代理。

---

### 1.4 事件

| 扩展点 | 时机 | Microsphere 用法 |
|---|---|---|
| `ApplicationListener` | 应用事件 | Zone 切换、配置刷新、注册事件 |
| `@EventListener` | 注解监听 | 注册生命周期、配置变更 |
| `ApplicationEventMulticaster` | 事件广播 | 同步/异步事件 |
| `EnvironmentChangeEvent` | 配置变更 | Feign 自动刷新、Zone 切换 |

**关键点**：

- 事件发布过早，监听器可能还没注册；
- 事件是同步的，监听器异常会影响发布方（除非异步 multicaster）；
- `@EventListener` 的 order 和事务阶段要明确。

---

### 1.5 Web 阶段

| 扩展点 | 时机 | Microsphere 用法 |
|---|---|---|
| `WebMvcConfigurer` | MVC 配置 | 拦截器/参数解析器 |
| `HandlerInterceptor` | MVC 请求前后 | 治理拦截、日志 |
| `HandlerMethodArgumentResolver` | 参数解析 | 上下文注入 |
| `WebFilter` | WebFlux 过滤链 | 网关/响应式治理 |
| `HandlerMapping` | 请求映射 | 端点元数据收集 |

**关键点**：MVC 与 WebFlux 是两套扩展点，不能混用。

---

### 1.6 Spring Boot 扩展

| 扩展点 | 用途 | Microsphere 用法 |
|---|---|---|
| `AutoConfiguration.imports` | 自动配置清单 | 各仓库的自动配置入口 |
| `spring.factories` | 旧式扩展 | 兼容旧版本 |
| `@AutoConfiguration` + `@AutoConfigureBefore/After` | 自动配置顺序 | 注册/发现/网关 |
| `BindListener` | 配置属性绑定监听 | `microsphere-spring-boot` |
| `FailureAnalyzer` | 启动失败诊断 | 依赖冲突/Bean 冲突 |
| Actuator `@Endpoint` | 运维端点 | 注册/配置/路由端点 |

**关键点**：自动配置类不是普通 `@Configuration`，它的顺序和条件决定了“是否生效”。

---

### 1.7 Spring Cloud 扩展

| 扩展点 | 用途 | Microsphere 用法 |
|---|---|---|
| `ServiceRegistry` | 注册 | `MultipleServiceRegistry` |
| `Registration` | 实例信息 | `MultipleRegistration` |
| `DiscoveryClient` | 发现 | `UnionDiscoveryClient` |
| `ServiceInstanceListSupplier` | 实例过滤 | Zone/权重 |
| `LoadBalancerClientFactory` | 客户端 LB 上下文 | 多活/自定义 LB |
| Feign `Capability` | Feign 组件定制 | 自动刷新 |
| `RouteDefinitionLocator` | 网关路由 | 动态路由 |
| `GlobalFilter` / `GatewayFilterFactory` | 网关过滤 | 端点级路由 |

---

## 2. 从扩展点看 Microsphere 各仓库

| 仓库 | 主要扩展点 | 关键类 |
|---|---|---|
| `microsphere-spring-boot` | AutoConfiguration / BindListener / Actuator | `ConfigurableAutoConfigurationImportFilter`、`BindListener` |
| `microsphere-spring` | BeanFactoryListener / Environment / Web | `BeanFactoryListener`、`ResourcePropertySource`、`WebEndpointMapping` |
| `microsphere-spring-cloud` | ServiceRegistry / DiscoveryClient / Feign | `ServiceRegistryAutoConfiguration`、`DiscoveryClientAutoConfiguration`、`FeignClientAutoRefreshAutoConfiguration` |
| `microsphere-configuration` | PropertySourceLoader | `NacosPropertySourceLoader`、`EtcdPropertySourceLoader` |
| `dynamic` | ImportSelector / Bean 定义 | `EnableDynamicJdbcAutoConfiguration` |
| `microsphere-multiactive` | Listener / LoadBalancer | `ZoneContextChangedListener`、`ZonePreferenceServiceInstanceListSupplier` |
| `sentinel` | AutoConfiguration / Plugin | `SentinelSpringWebAutoConfiguration` |
| `microsphere-resilience4j` | ImportRegistrar / Interceptor | `EnableCircuitBreaker`、`Resilience4jHandlerMethodInterceptor` |
| `microsphere-gateway` | RouteDefinitionLocator / GlobalFilter | `WebEndpointMappingGatewayAutoConfiguration`、`WebEndpointMappingGlobalFilter` |
| `microsphere-redis` | BeanPostProcessor / Interceptor | `RedisConnectionFactoryProxyBeanPostProcessor` |
| `microsphere-mybatis` | ImportRegistrar / BPP | `EnableMyBatis`、`SqlSessionFactoryBeanPostProcessor` |
| `microsphere-druid` | BeanPostProcessor | `DruidDataSourceBeanPostProcessor` |

---

## 3. 扩展点执行顺序图

```
EnvironmentPostProcessor
  → PropertySourceLoader
  → ApplicationContextInitializer
  → BeanDefinitionRegistryPostProcessor
  → BeanFactoryPostProcessor
  → BeanPostProcessor (before/after)
  → SmartInitializingSingleton
  → ContextRefreshedEvent
  → ApplicationRunner
```

**读源码时先定位阶段，再追类调用。** 阶段错了，结论一定错。

---

## 4. 四个典型链路

### 4.1 多注册中心

```
AutoConfiguration.imports
  → ServiceRegistryAutoConfiguration
  → MultipleRegistration / MultipleServiceRegistry
  → ServiceRegistry.register()
  → EventPublishingRegistrationAspect
```

### 4.2 Feign 动态刷新

```
@EnableFeignAutoRefresh
  → Import(Marker)
  → FeignClientAutoRefreshAutoConfiguration
  → FeignComponentRegistry
  → EnvironmentChangeEvent
  → 重建 Encoder/Decoder/Retryer
```

### 4.3 Zone 切换

```
EnvironmentChangeEvent
  → ZoneContextChangedListener
  → ZoneContext.setZone()
  → ZonePreferenceServiceInstanceListSupplier
  → LoadBalancer 选实例
```

### 4.4 Redis 代理

```
BeanPostProcessor
  → RedisConnectionFactoryProxyBeanPostProcessor
  → JDK/AOP 代理
  → RedisCommandInterceptor
  → Kafka 复制
```

---

## 5. 十二个坑

1. **BPP 当 BeanFactoryPostProcessor**：阶段不同，能拿到的 Bean 不同。
2. **`@ConditionalOnMissingBean` 顺序错**：自动配置顺序决定条件结果。
3. **`@AutoConfigureBefore/After` 写错**：条件装配会误判。
4. **EnvironmentPostProcessor 依赖普通 Bean**：此时 Bean 还没创建。
5. **BPP 里提前初始化 Bean**：循环依赖/提前代理。
6. **AOP 自调用失效**：同类内部调用不过代理。
7. **事件发布太早**：监听器还没注册。
8. **同步事件异常影响发布方**：需要异步/隔离时明确。
9. **MVC 扩展点用到 WebFlux**：两套模型不通用。
10. **Feign Capability 当 Interceptor**：职责不同，刷新行为不同。
11. **Gateway Filter 顺序错**：鉴权/限流/转发顺序会反转。
12. **数据面 BPP 代理错对象**：代理 DataSource 还是 Connection，语义不同。

---

## 6. 一句话

**Spring 扩展点不是 API 清单，而是执行顺序图；Microsphere 的“为什么没生效”，十有八九是挂错了阶段。**

下一篇：**04 AutoConfiguration 与条件装配**。
