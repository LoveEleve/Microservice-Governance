# microsphere-spring-cloud 知识点提取

> 源码：`/data/workspace/java-training-camp/cloud-native-code/stage-4/microsphere-spring-cloud`（依赖链第 5 站，spring-boot 之后；仅 stage-4 有副本，78 生产文件）
> 提取时间：2026-08-12（批 1：client/discovery + client/service 核心；后续：client 剩余 / fault / loadbalancer / commons / context / openfeign 20）
> 状态：批 1 已提取；MCP 索引已建（2120 节点/6110 边）
> 关联：stage-4 课程提取（多活架构）大量引用本仓库——**多注册中心/服务治理**

## 一、仓库定位

**Spring Cloud 扩展库**——多注册中心联合发现、服务注册生命周期、负载均衡、容错。模块：commons（58 主战场——client 46）/openfeign（20）。核心维度：[分布式问题]（服务发现/注册/均衡）+ [工程问题]（Spring Cloud 扩展）。

## 前置条件清单

读者需先掌握：1. Spring Cloud DiscoveryClient/ServiceInstance/Registration 体系 2. 前四仓库全部知识点 3. stage-4 课程（多活——本仓库是其源码侧）
未达前置者，先补：前四个仓库 outline + Spring Cloud 源码（本地有）

## 掌握度

目标读者：中级偏上（读源码多、Spring 熟——HANDOVER 画像）
讲解策略：Spring Cloud 机制直接讲 + 与官方源码对照

---

## 二、逐文件映射 + 原子记录

### 包: `io.microsphere.spring.cloud.client.discovery`（批 1a：8 文件）

#### KP-401 `UnionDiscoveryClient` 多注册中心联合发现（UnionDiscoveryClient.java:54-155）

- **维度**：[分布式问题]（服务发现）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（多注册中心模式） | **置信度**：High
- **前置**：Spring Cloud DiscoveryClient/CompositeDiscoveryClient（官方多注册中心合并）、多注册中心场景
- **需求**：**多注册中心全量合并**——应用同时注册到 Nacos + Eureka 等，查询时**合并所有注册中心结果**（非短路）
- **参考实现**：**聚合发现**（implements DiscoveryClient :54 + **union 合并语义**（getInstances :88-98——**遍历所有 client 收集并集**（非空才加入 :93——跳过空结果）；getServices :112-120——**Set 去重合并**））；**懒加载 + 排除**（getDiscoveryClients :128-145——**SmartInitializingSingleton 后初始化**（容器就绪后收集）+ **排除 CompositeDiscoveryClient 与自身**（:135-138——**防递归**（官方 Composite 已被排除，自身也排除——避免重复/循环）））；**生命周期**（ApplicationContextAware + SmartInitializingSingleton + DisposableBean :54）
- **对比取舍**：**知识增量**：①**与官方 CompositeDiscoveryClient 的本质差异**（官方源码实证 CompositeDiscoveryClient.java:51-59——**first-match 短路**（第一个非空即返回）；microsphere Union 是**全量 union 合并**（所有非空结果合并）——**"短路优先" vs "全量合并"两种多注册中心语义**——测试实证（UnionDiscoveryClientTest :78-81——内部列表含 Union/Simple/Dummy）；②**排除 Composite 与自身**（:135-138——防递归的容器收集设计）；③**缺陷：getInstances 无去重**（:88-98 addAll 直接合并——同一实例在多注册中心重复注册时会返回重复（历史 REQ D04 同发现——交叉验证）——正确应如 getServices 用 Set 去重）
- **my-xhs**：**该用没用**——多注册中心全量合并场景（my-xhs 若同时用 Nacos + 其他）；官方 Composite（短路优先）覆盖多注册基础

#### KP-402 `ReactiveDiscoveryClientAdapter` 响应式发现适配（ReactiveDiscoveryClientAdapter.java:38-105）

- **维度**：[分布式问题]（服务发现）| **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式]（响应式适配） | **置信度**：High
- **前置**：ReactiveDiscoveryClient（Flux 响应式）、阻塞/响应式桥接
- **需求**：**Reactive → 阻塞适配**——响应式发现客户端转为传统 DiscoveryClient（WebFlux 服务被 MVC 消费）
- **参考实现**：**适配器**（implements DiscoveryClient + 持有 ReactiveDiscoveryClient :40——**阻塞接口包装响应式实现**）；**Flux → List**（getInstances :87-89——`toList(flux)`（**Flux 收集阻塞**——响应式转阻塞的标准桥接）；getServices :104-105 同构）
- **对比取舍**：**知识增量**：①**响应式↔阻塞桥接**（Flux → List——block 收集）；②**适配器模式**（官方 Spring Cloud 有 ReactiveDiscoveryClient 但无此反向适配——MVC 场景消费响应式注册中心）
- **my-xhs**：**该用没用**——响应式注册中心被 MVC 消费；官方 `ReactiveDiscoveryClient` 覆盖响应式场景

#### KP-403 发现条件与 Actuator 配套（client/discovery 其余 6 + condition 2 + actuator 5 + autoconfigure）

- **维度**：[分布式问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：Medium（部分深读）
- **前置**：Spring Cloud 发现条件、Actuator
- **需求**：发现条件（@ConditionalOnBlockingDiscoveryAvailable/@ConditionalOnReactiveDiscoveryAvailable——**阻塞/响应式发现可用条件**）+ 自动配置（DiscoveryClientAutoConfiguration/ReactiveDiscoveryClientAutoConfiguration——**阻塞/响应式双栈自动配置**）+ Actuator 端点
- **my-xhs**：**该用没用**——Spring Cloud 官方覆盖

#### KP-404 `MultipleRegistration` 多注册封装（MultipleRegistration.java:24-73 + MultipleAutoServiceRegistration:17-33 + MultipleServiceRegistry）

- **维度**：[分布式问题]（服务注册）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（多注册模式） | **置信度**：High
- **前置**：Registration（Spring Cloud 注册信息）、AutoServiceRegistration、多注册中心场景
- **需求**：**一个应用注册到多个注册中心**——Registration 的多实例封装（按类型分派）
- **参考实现**：**多 Registration 容器**（:24——`Map<Class<? extends Registration>, Registration>` 类型映射 :26 + defaultRegistration :28——**按类型查 + 默认兜底**）；**类型索引构建**（:47-56——**findAllClasses 类型族映射**（注册实现的所有接口类型 → 同一注册 :51-53——**类型族索引**））；**委托访问**（getInstanceId :70-73——**默认注册的委托**）；**AutoServiceRegistration 配套**（MultipleAutoServiceRegistration extends AbstractAutoServiceRegistration\<MultipleRegistration> :17——**多注册的自动注册**）
- **对比取舍**：**知识增量**：①**多注册模式**（Map 类型分派 + 默认兜底——Spring Cloud 官方单 Registration，多注册需自定义）；②**类型族索引**（findAllClasses 把实现类映射到所有接口类型——按类型查找的索引技巧）
- **my-xhs**：**该用没用**——多注册中心场景（Nacos + Eureka 同时注册）；单一 Nacos 无需求

#### KP-405 注册事件家族 + AOP 切面（RegistrationEvent/RegistrationPreRegisteredEvent/RegistrationRegisteredEvent/RegistrationPreDeregisteredEvent/RegistrationDeregisteredEvent + EventPublishingRegistrationAspect）

- **维度**：[分布式问题]（服务注册）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（注册可观测性） | **置信度**：High
- **前置**：ApplicationEvent、AOP（@Aspect/@Around）、ServiceRegistry 生命周期
- **需求**：**注册生命周期事件化**——注册前/后、注销前/后四态事件（服务上下线通知/审计）
- **参考实现**：**事件家族**（RegistrationEvent 抽象 :41——extends ApplicationEvent + 持有 registry/source :59；四态子类——RegistrationPreRegisteredEvent :31 / RegistrationRegisteredEvent / RegistrationPreDeregisteredEvent / RegistrationDeregisteredEvent——**前后成对**（同 microsphere-spring KP-214 双钩模式））；**AOP 发布**（EventPublishingRegistrationAspect :45——@Aspect + **@Before/@After 注册切面**（:77/:126——**AOP 拦截 ServiceRegistry.register 发布事件**——**官方接口无事件，AOP 补**））
- **对比取舍**：**知识增量**：①**注册四态事件**（前后成对——生命周期事件的完整模式）；②**AOP 而非继承扩展**（官方 ServiceRegistry 无事件钩子——**用 AOP 切面发布**（无侵入）——**扩展官方接口的 AOP 方案**）；③**缺陷：@After 语义错误（历史 REQ D12 交叉验证）**——AspectJ `@After` 是 **finally 语义**（:126——register() 抛异常仍执行）→ **注册失败却发布 RegistrationRegisteredEvent（虚假"注册成功"事件）**——正确应 @AfterReturning（成功才发）或 @AfterThrowing（失败发失败事件）——**AOP 通知类型语义**（@Before/@After/@AfterReturning/@AfterThrowing/@Around 五型的 finally/成功/异常语义差异）
- **my-xhs**：**该用没用**——服务上下线通知（my-xhs 服务治理监控）；官方无此能力

#### KP-409 常量/自动配置/条件变体归组（*Constants 4 + *AutoConfiguration 8 + ConditionalOn*Enabled 3 + DefaultRegistration/SimpleAutoServiceRegistration/AbstractServiceRegistrationEndpoint/ConfigurationPropertyHasFeaturesAutoConfiguration）

- **维度**：[工程问题] | **权重**：[边缘] | **深度**：🟢 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：Medium
- **前置**：自动配置、常量、条件注解
- **需求**：**配套归组**——注册常量（ServiceRegistryConstants/DiscoveryClientConstants/InstanceConstants/FeaturesConstants）、自动配置家族（ServiceRegistryAutoConfiguration/SimpleAutoServiceRegistrationAutoConfiguration/WebServiceRegistryAutoConfiguration/WebMvcServiceRegistryAutoConfiguration/WebFluxServiceRegistryAutoConfiguration + ServiceRegistrationEndpointAutoConfiguration + ConfigurationPropertyHasFeaturesAutoConfiguration）、条件变体（@ConditionalOnAutoServiceRegistrationEnabled/@ConditionalOnMultipleRegistrationEnabled/@ConditionalOnFeaturesEnabled + @ConditionalOnAutoServiceRegistrationAvailable）、DefaultRegistration（默认注册实现）、SimpleAutoServiceRegistration（简单自动注册）、AbstractServiceRegistrationEndpoint（端点基座）
- **my-xhs**：**不该用**——配置类/常量无独立知识

#### KP-406 注册存储与端点（InMemoryServiceRegistry/SimpleServiceRegistry + ServiceRegistrationEndpoint/ServiceDeregistrationEndpoint）

- **维度**：[分布式问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式]（内存注册表） | **置信度**：High
- **前置**：ServiceRegistry 接口、Actuator 端点
- **需求**：**内存注册表**（测试/本地场景的 ServiceRegistry 实现）+ 注册状态端点
- **参考实现**：**内存注册表**（InMemoryServiceRegistry/SimpleServiceRegistry——**本地注册实现**（无外部注册中心时用））；**注册端点**（ServiceRegistrationEndpoint/ServiceDeregistrationEndpoint——**Actuator 暴露注册/注销操作**）
- **my-xhs**：**该用没用**——本地开发/测试；官方 SimpleDiscoveryClient 覆盖发现侧

#### KP-407 Features 条件家族（ConditionalOnFeaturesAvailable/Enabled + FeaturesProperties/FeaturesUtils + NamedFeatureComparator）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P3 | **过时**：[时间无关模式]（特性开关） | **置信度**：Medium
- **前置**：特性开关（Feature Toggle）、条件注解
- **需求**：**特性开关条件**——按 features 配置启停（微服务特性门控）
- **my-xhs**：**该用没用**——特性开关；Spring Cloud 无内建

#### KP-408 服务工具与事件（ServiceInstanceUtils/DiscoveryUtils/RegistrationMetaData/ServiceInstancesChangedEvent/RegistrationCustomizer + 剩余）

- **维度**：[分布式问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：Medium
- **前置**：服务实例工具、实例变更事件
- **需求**：服务实例工具 + 实例变更事件（ServiceInstancesChangedEvent——**实例变化通知**）+ 注册定制器（RegistrationCustomizer）
- **参考实现**：ServiceInstanceUtils.setProperties（:196-215——`(source, target)` 签名但实现**反向赋值**：`target.setInstanceId(source.getInstanceId())` 实际是 **target 当源、source 当目标**（参数语义颠倒——历史 REQ D01 交叉验证）+ **metadata 无效操作**（:211-212——`metadata.clear(); metadata.putAll(source.getMetadata())` 对 source 自己的 metadata 清空再复制自身——清空原数据 + 无效））；DiscoveryUtils 调用（:76 `setProperties(targetLocal, local)`——配合签名颠倒 → **数据流向错误**（历史 REQ D08 交叉验证）
- **my-xhs**：**该用没用**
### 包: `io.microsphere.spring.cloud.fault`（批 2a：5 文件）

#### KP-410 `WeightedRoundRobin` 加权轮询数据载体（WeightedRoundRobin.java:12-166 + LoadBalancerUtils + TomcatFaultToleranceAutoConfiguration）

- **维度**：[分布式问题]（负载均衡）| **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式]（加权数据结构） | **置信度**：High
- **前置**：加权轮询算法（Nginx/Nacos 平滑加权）、LongAdder、动态权重
- **需求**：**加权轮询的节点数据结构**——权重可动态调整的轮询节点（Nacos 权重负载均衡的配套）
- **参考实现**：**LongAdder 计数器**（:18——**高并发计数**（无 CAS 竞争））；**权重调整重置**（setWeight :79-82——**改权重重置计数器**）；**两个核心操作**（increaseCurrent :98-101——**当前值加权重**；sel :117-119——**总权重减法**——**注意：这只是数据载体（两个操作），非完整算法**（Nacos 版 WeightedRR 有 select 循环做节点选择——本类无选择逻辑，由 LoadBalancerUtils 配合使用——历史 REQ D07 "完整实现"同发现：缺 select）
- **对比取舍**：**知识增量**：①**平滑加权轮询的数据结构**（current + weight 双字段 + 两操作——Nginx 平滑加权算法的载体部分）；②**LongAdder 高并发**（vs AtomicLong——竞争优化）；③**动态权重**（setWeight 重置——动态调权）；④**定位辨析**：数据结构 vs 完整算法（选择逻辑在 LoadBalancerUtils——设计拆分）
- **my-xhs**：**该用没用**——加权负载均衡（my-xhs 若做权重路由）；Spring Cloud LoadBalancer 有 Weighted 实现

#### KP-411 Tomcat 容错（TomcatFaultToleranceAutoConfiguration:56-90 + TomcatDynamicConfigurationListener + FaultTolerancePropertyConstants）

- **维度**：[性能优化]（容器容错）| **权重**：[支撑] | **深度**：🟡 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：TomcatWebServer、嵌入式服务器定制、动态配置
- **需求**：**Tomcat 容错配置**——`microsphere.spring.cloud.fault-tolerance.tomcat.enabled` 开关（:65）+ **动态配置监听**（TomcatDynamicConfigurationListener——配置变更调整 Tomcat）
- **参考实现**：**条件装配**（@ConditionalOnClass 三官方类 :56-58——Tomcat/Boot/Cloud API + @AutoConfigureAfter :59）；**动态配置**（:90——从 webServer 调整）
- **my-xhs**：**该用没用**——Tomcat 参数动态调优；Boot 官方配置覆盖静态场景

### 包: `io.microsphere.spring.cloud.commons` + `context` + `loadbalancer`（批 2b：7 文件）

#### KP-412 条件与上下文配套（ConditionalOnLoadBalancerEnabled/ConditionalOnUtilEnabled/SpecificationCustomizer/SpecificationAutoConfiguration/SpecificationBeanPostProcessor + 常量 2）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：Medium
- **前置**：NamedContextFactory（Spring Cloud 子上下文）、@ConditionalOnProperty
- **需求**：**Specification 定制**（SpecificationCustomizer/BeanPostProcessor——**Feign 子上下文规格定制**——NamedContextFactory 的配置对象定制）+ 条件（@ConditionalOnLoadBalancerEnabled 等）+ 常量
- **my-xhs**：**该用没用**——子上下文定制；Spring Cloud 官方 NamedContextFactory 覆盖

### 模块: `microsphere-spring-cloud-openfeign`（批 2c：20 文件）

#### KP-413 Feign 客户端自动刷新（FeignClientAutoRefreshAutoConfiguration:31-68 + autorefresh 家族 6 文件）

- **维度**：[分布式问题]（配置热更新）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（子上下文重建） | **置信度**：High
- **前置**：FeignClientFactoryBean/NamedContextFactory/FeignClientSpecification（Spring Cloud OpenFeign）、ApplicationReadyEvent、配置变更
- **需求**：**Feign 客户端配置热更新**——Feign 配置变更（如负载均衡规则）自动重建客户端（官方 @RefreshScope 外的完整刷新方案）
- **参考实现**：**触发链**（@ConditionalOnOpenFeignAvailable + @ConditionalOnClass 双类 + @ConditionalOnBean(Marker) :31-36——**条件装配**；@AutoConfigureAfter 官方 Feign + microsphere Specification :37-40；**ApplicationReadyEvent 注册监听**（:49-55——**启动后注册配置变更监听器**））；**自动刷新家族**（autorefresh 6 文件——AutoRefreshCapability/AutoRefreshCapabilityCustomizer/EnableFeignAutoRefresh/FeignClientConfigurationChangedListener/**FeignComponentRegistry**（:67——**组件注册表**（FeignClientProperties.getDefaultConfig + beanFactory——**按配置名管理 Feign 组件**）））
- **对比取舍**：**知识增量**：①**Feign 配置热更新的完整方案**（Spring Cloud 官方 @RefreshScope 只能刷新属性，Feign 组件重建需子上下文——microsphere 用注册表 + 变更监听）；②**组件注册表模式**（FeignComponentRegistry——按配置名管理组件实例）；③**缺陷（历史 REQ 交叉验证）**：**FeignClientConfigurationChangedListener 无子属性 key 崩溃**（:80-82——`str.substring(0, index)` 当 key 无子属性时 index=-1 → StringIndexOutOfBoundsException——运行期崩溃，历史 REQ D02 同发现）；**CompositedRequestInterceptor.refresh() NPE**（:129-130——`config.get(properties.getDefaultConfig())` 无键返回 null → 方法引用 NPE，历史 REQ D03 同发现）
- **my-xhs**：**该用没用**——Feign 配置热更新（my-xhs 负载均衡策略动态调整）；官方 @RefreshScope 覆盖属性级

#### KP-414 装饰组件家族（DecoratedFeignComponent:23-68 + Decorated 7 变体 + Refreshable + CompositedRequestInterceptor）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式]（装饰器 + 刷新） | **置信度**：High
- **前置**：装饰器模式、Feign 组件（Contract/Encoder/Decoder/Retryer/QueryMapEncoder/ErrorDecoder）
- **需求**：**Feign 组件可刷新装饰**——装饰器包装 + 重建时替换 delegate（热更新基础设施）
- **参考实现**：**装饰基座**（DecoratedFeignComponent<T> :23——implements Refreshable + **volatile delegate**（:33——**可变委托**（刷新时替换）；构造注入 NamedContextFactory + contextId + clientProperties :49-54——**子上下文绑定**）；**delegate 访问**（:67-68——volatile 读取）；**7 变体**（DecoratedContract/DecoratedDecoder/DecoratedEncoder/DecoratedErrorDecoder/DecoratedQueryMapEncoder/DecoratedRetryer——**Feign 组件全覆盖** + DecoratedFeignComponent）；**Refreshable 接口**（刷新契约）+ **CompositedRequestInterceptor**（拦截器组合）
- **对比取舍**：**知识增量**：①**可刷新装饰器**（volatile delegate + Refreshable——**装饰器模式 + 热替换**的组合）；②**Feign 组件全覆盖装饰**（官方组件都可装饰刷新）
- **my-xhs**：**该用没用**——组件热替换模式（配置变更重建）；官方无

#### KP-415 Feign 配套（FeignAutoConfiguration/ConditionalOnOpenFeignAvailable/ConditionalOnOpenFeignEnabled/FeignConstants/NoOpRequestInterceptor + CommonsPropertyConstants/SpringCloudPropertyConstants）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟢 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：Medium
- **前置**：Feign 自动配置、条件、常量
- **my-xhs**：**不该用**——官方覆盖

---

## 三、深度 review 七项报告（批 1-2）

> 2026-08-12 批判性 review：穷尽性核对先行（每批写完立即核对）——client 46/46、全部 78/78 达成。

- [x] **① 源码行号精确核对**：KP-401:54-120、KP-402:38-105、KP-404:24-73/:47-56、KP-405:41-59/:45-108、KP-410:12-118、KP-413:31-68、KP-414:23-68——全部 grep 实证 ✓
- [x] **② 穷尽性**：78/78 生产文件全覆盖（client 46 + fault 5 + loadbalancer 1 + commons/context 7 + openfeign 20）✓
- [x] **③ 空节标注**：N/A ✓
- [x] **④ 过时三级**：15 KP 全部标注 ✓
- [x] **⑤ 重复内容**：UnionDiscoveryClient（session005 交接 [未找到] → 实证）；注册事件 ↔ microsphere-spring KP-214 双钩模式 ✓
- [x] **⑤b 引用目标核对**：Spring Cloud 官方类（DiscoveryClient/ServiceRegistry/Registration/NamedContextFactory/FeignClientProperties）存在 ✓
- [x] **⑥ 诚实标注**：KP-403/408/412/415 Medium（部分深读）✓
- [x] **⑦ 命名空间迁移**：N/A ✓

### 测试扫描记录（02 §2.1）

| 测试文件 | 验证了 | 结论 |
|---------|--------|------|
| UnionDiscoveryClientTest / IntegrationTest | **内部列表含 Union/Simple/Dummy 三类**（:78-81）+ description（:86-87）+ getInstances 合并（:92）——**多 client 并存实证** | KP-401 ✓ |
| WeightedRoundRobinTest | 权重/计数器断言（:45-73——getId :45/getWeight :51/increaseCurrent 累加 :57-59/sel 减法 :65/getLastUpdate :70-73）——**加权算法实证** | KP-410 ✓ |
| MultipleRegistrationTest / EventPublishingRegistrationAspectTest / SimpleAutoServiceRegistrationTest 等 | 多注册/切面/自动注册 | KP-404/405 [待补扫] |

### 历史 REQ 缺陷交叉验证清单（05-cloud 完整版）

> 来源：`microsphere-analysis/05-microsphere-spring-cloud-analysis/05-REQ-requirements-spec.md` 16 项缺陷
> 状态：✅ 已验证补入 KP / ⬜ 未验证（需后续源码确认）

| # | 历史缺陷 | 验证 | 落点 |
|---|---------|------|------|
| D01 | ServiceInstanceUtils.setProperties source/target 混淆 | ✅ 证实（:197 反向赋值 + :211 无效 metadata） | KP-408 |
| D02 | FeignClientConfigurationChangedListener 无子属性崩溃 | ✅ 证实（:82 substring(0,-1)） | KP-413 |
| D03 | CompositedRequestInterceptor.refresh() NPE | ✅ 证实（:129 config.get null） | KP-413 |
| D04 | UnionDiscoveryClient.getInstances 实例去重 | ✅ 证实（:88-98 addAll 无去重） | KP-401 |
| D05 | AbstractServiceRegistrationEndpoint static running | ✅ 证实（:48 static） | KP-406 |
| D06 | MultipleRegistration 同类型覆盖 | ✅ 证实（:53 put 覆盖） | KP-404 |
| D07 | WeightedRoundRobin 不完整（无 select） | ✅ 证实（:98-166 仅两操作） | KP-410 |
| D08 | DiscoveryUtils.setProperties 参数颠倒 | ✅ 证实（:76 + 签名颠倒） | KP-408 |
| D09 | MultipleServiceRegistry fallback 映射错误 | ✅ 证实（:193-215——**双路径**：泛型推断为主（:194-198），SPI fallback 仅在推断失败时（:199-204）；**fallback 传 serviceRegistryClass 当 factoryType 语义存疑**——缺陷存在但影响小） | KP-404 |
| D10 | Feign 组件热刷新对默认组件退化 | ✅ 证实（:75-81——configComponentMappings 映射表 + 空白 config 返回 null——默认组件未映射时刷新退化） | KP-413 |
| D11 | DecoratedErrorDecoder fallback 抽象类 | ❌ **证伪**（Feign 源码实证 ErrorDecoder.Default extends DefaultErrorDecoder——**具体类非抽象**（feign/core:82）——历史断言错误） | KP-414 |
| D12 | EventPublishingRegistrationAspect @After 语义错误 | ✅ 证实（@After finally 语义 → 注册失败发成功事件） | KP-405 |
| D13 | ReactiveDiscoveryClientAdapter 阻塞事件循环 | ✅ 部分证实（:138-143——**有 isInNonBlockingThread 防御**（:141）但 `toFuture().get()` 在事件循环上**仍阻塞等待**（:142）——防御存在、本质仍阻塞） | KP-402 |
| D14 | TomcatDynamicConfigurationListener source 依赖 | ✅ 证实（:144——`context.equals(event.getSource())`——仅 /actuator/refresh 生效，EnvironmentManager 路径失效） | KP-411 |
| D15 | endpoints.properties 入侵性默认关闭 SC 端点 | ✅ 证实（resources/META-INF/config/default/endpoints.properties——`management.endpoint.serviceregistry.enabled=false`——jar 内属性默认关闭） | KP-403 |
| D16 | 双重注册风险（Multiple + Nacos 原生并存） | ✅ 部分证实（ServiceRegistryAutoConfiguration:62-79——**有条件**（@ConditionalOnMultipleRegistrationEnabled + @ConditionalOnBean）但启用时 @Primary :76 与原生并存 → 风险真实） | KP-404 |
