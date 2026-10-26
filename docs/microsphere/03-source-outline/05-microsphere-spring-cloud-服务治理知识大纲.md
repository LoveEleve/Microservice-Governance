# Microsphere Spring Cloud 服务治理扩展知识大纲（spring-cloud 触发面）

> 来源：`mapping/05-microsphere-spring-cloud.md` 15 个 KP 的知识本体提炼
> 性质：**源码侧 outline（L1.5）**——mapping 是文件映射（过程），本大纲是知识本体（结果）
> 组织：按知识维度，非按文件；每个知识点标注来源 KP + 六元元数据 + 现代替代
> 用途：①自学/面试知识图谱（Spring Cloud 服务治理）②与课程 L1（stage-3/4）合并成 L3 的源码侧素材
> 覆盖核对：15/15 KP 全部归属（文末核对表）

---

## 一、多注册中心与发现（服务发现核心）[分布式问题]

> **核心命题**：多注册中心场景的两种合并语义——**短路优先（官方）vs 全量合并（microsphere）**。

### 1.1 多注册中心两种语义 [🔴 P1] [时间无关模式]
- **来源**：KP-401（UnionDiscoveryClient）+ 官方 CompositeDiscoveryClient
- **机制**：**官方 CompositeDiscoveryClient = first-match 短路**（第一个非空即返回——官方源码实证 :51-59）；**microsphere Union = 全量 union 合并**（所有非空结果并集 :88-98）+ **排除 Composite 与自身防递归**（:135-138）
- **对比**：两种语义的适用场景（短路=快速响应首选源；全量=完整视图）
- **my-xhs**：该用没用——多注册中心全量合并；官方 Composite 覆盖短路场景

### 1.2 响应式发现适配 + 发现配套 [🔴 P2] [时间无关模式]
- **来源**：KP-402（ReactiveDiscoveryClientAdapter）/ KP-403
- **机制**：**Reactive → 阻塞桥**（Flux → toList 收集 :87-89——响应式注册中心被 MVC 消费）；发现条件（@ConditionalOnBlocking/ReactiveDiscoveryAvailable——双栈自动配置）
- **my-xhs**：该用没用——响应式↔阻塞桥接

---

## 二、服务注册生命周期（注册可观测性）[分布式问题]

> **核心命题**：官方 ServiceRegistry 接口无事件/切面——microsphere 用 **AOP + 事件**补注册可观测性。

### 2.1 注册四态事件 + AOP 发布 [🔴 P1] [时间无关模式]
- **来源**：KP-405（RegistrationEvent 家族 + EventPublishingRegistrationAspect）
- **机制**：**四态事件**（RegistrationPreRegistered/Registered/PreDeregistered/Deregistered——前后成对）+ **@Aspect 拦截 ServiceRegistry.register 发布**（:45-108——**官方接口无事件，AOP 无侵入补**）
- **生态呼应**：microsphere-spring KP-214（before/after 双钩模式）在注册域的落地
- **my-xhs**：该用没用——服务上下线通知

### 2.2 多注册封装 + 注册存储 [🔴 P1] [时间无关模式]
- **来源**：KP-404（MultipleRegistration）/ KP-406（InMemoryServiceRegistry）
- **机制**：**多 Registration 容器**（Map 类型分派 :26 + 类型族索引 findAllClasses :51-53 + 默认兜底）；**内存注册表**（本地/测试场景 ServiceRegistry 实现）+ 注册端点
- **my-xhs**：该用没用——多注册中心/本地开发

---

## 三、负载均衡与容错 [分布式问题]

### 3.1 平滑加权轮询 [🔴 P2] [时间无关模式]
- **来源**：KP-410（WeightedRoundRobin）
- **机制**：**current + weight 双字段**（Nginx 平滑加权算法）+ **LongAdder 高并发**（:18）+ **动态权重**（setWeight 重置 :79-82）
- **my-xhs**：该用没用——加权路由；Spring Cloud LoadBalancer Weighted 实现覆盖

### 3.2 Tomcat 容错动态配置 [🟡 P3] [时间无关模式]
- **来源**：KP-411
- **机制**：条件装配（@ConditionalOnClass 三官方类 :56-58）+ 动态配置监听（TomcatDynamicConfigurationListener）
- **my-xhs**：该用没用——Tomcat 参数动态调优

---

## 四、Feign 配置热更新（子上下文重建）[分布式问题]

> **核心命题**：Feign 配置变更（如负载均衡规则）的**完整重建方案**——官方 @RefreshScope 只能刷属性，组件重建需子上下文。

### 4.1 Feign 组件注册表 + 可刷新装饰器 [🔴 P1] [时间无关模式]
- **来源**：KP-413（FeignClientAutoRefreshAutoConfiguration）/ KP-414（DecoratedFeignComponent）
- **机制**：**组件注册表**（FeignComponentRegistry——按配置名管理 Feign 组件 :67）+ **可刷新装饰器**（DecoratedFeignComponent<T>——volatile delegate :33 + Refreshable——装饰器模式 + 热替换组合）+ **7 组件全覆盖**（Contract/Decoder/Encoder/ErrorDecoder/QueryMapEncoder/Retryer）+ 变更监听（FeignClientConfigurationChangedListener）
- **触发链**：条件装配（@ConditionalOnOpenFeignAvailable :31）+ ApplicationReadyEvent 注册监听（:49-55）
- **my-xhs**：该用没用——Feign 配置热更新（负载均衡策略动态调整）

---

## 五、特性开关与服务工具 [工程问题]

### 5.1 Features 特性开关条件 [🟡 P3] [时间无关模式]
- **来源**：KP-407（ConditionalOnFeaturesAvailable/Enabled + FeaturesProperties/FeaturesUtils + NamedFeatureComparator）
- **机制**：**特性门控**（按 features 配置启停——@ConditionalOnFeaturesAvailable/@ConditionalOnFeaturesEnabled 条件 + FeaturesProperties 配置载体 + NamedFeatureComparator 排序）
- **对比**：Spring Cloud 无内建特性开关——功能门控（Feature Toggle）模式
- **my-xhs**：该用没用——特性开关（灰度/功能门控）

### 5.2 服务工具与实例变更事件 [🟢 P3] [时间无关模式]
- **来源**：KP-408（ServiceInstanceUtils/DiscoveryUtils/RegistrationMetaData/ServiceInstancesChangedEvent/RegistrationCustomizer）
- **机制**：**实例变更通知**（ServiceInstancesChangedEvent——实例上下线事件）+ 服务实例工具（ServiceInstanceUtils/DiscoveryUtils）+ 注册元数据（RegistrationMetaData）+ 注册定制器（RegistrationCustomizer——注册信息定制）
- **my-xhs**：该用没用——实例变更监听（服务上下线感知）

### 5.3 Specification 定制（子上下文规格）[🟡 P3] [时间无关模式]
- **来源**：KP-412（SpecificationCustomizer/SpecificationAutoConfiguration/SpecificationBeanPostProcessor + ConditionalOnLoadBalancerEnabled/ConditionalOnUtilEnabled）
- **机制**：**NamedContextFactory 子上下文规格定制**（SpecificationCustomizer + BeanPostProcessor——Feign/LoadBalancer 子上下文的配置对象定制）
- **my-xhs**：该用没用——子上下文定制；官方 NamedContextFactory 覆盖

### 5.4 配套归组（常量/自动配置/Feign 配套）[🟢 P3] [时间无关模式]
- **来源**：KP-409（*Constants 4 + *AutoConfiguration 8 + 条件变体）/ KP-415（FeignAutoConfiguration/ConditionalOnOpenFeign*）
- **机制**：常量（ServiceRegistryConstants/DiscoveryClientConstants 等）+ 自动配置家族（ServiceRegistry/Web/WebMvc/WebFlux 变体）+ Feign 条件与自动配置
- **my-xhs**：不该用——配置类/常量无独立知识

---

## 覆盖核对（15/15）

| 维度 | 覆盖 KP | 数 |
|------|---------|:--:|
| 一、多注册中心与发现 | 401,402,403 | 3 |
| 二、服务注册生命周期 | 404,405,406 | 3 |
| 三、负载均衡与容错 | 410,411 | 2 |
| 四、Feign 热更新 | 413,414 | 2 |
| 五、配套条件与工具 | 407,408,409,412,415 | 5 |

**去重后唯一 KP**：401-415 全部 = **15/15 ✓**
**无孤儿 KP** ✓
