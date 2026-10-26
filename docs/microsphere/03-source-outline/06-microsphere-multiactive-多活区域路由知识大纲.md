# Microsphere Multiactive 多活区域路由知识大纲（multiactive 触发面）

> 来源：`mapping/06-microsphere-multiactive.md` 16 个 KP 的知识本体提炼
> 性质：**源码侧 outline（L1.5）**——mapping 是文件映射（过程），本大纲是知识本体（结果）
> 组织：按知识维度，非按文件；每个知识点标注来源 KP + 六元元数据 + 现代替代 + **my-xhs 落地实证**
> 用途：①自学/面试知识图谱（多活/区域路由）②与课程 L1（stage-4 多活）合并成 L3 的源码侧素材 ③**my-xhs 对照（本仓库已被 my-xhs 整体移植——见 §六）**
> 覆盖核对：16/16 KP 全部归属（文末核对表）
> **2026-08-13 实证修正**：my-xhs 已整体移植 zone 机制（17 文件 + 4 测试）——"已用"判定全部代码实证，大纲同步更新

---

## 一、Zone 自动发现（区域感知源头）[分布式问题]

> **核心命题**：官方 SCL 需要手配 `spring.cloud.loadbalancer.zone`；microsphere 从**云元数据/环境自动发现**——supports/locate 两段式 SPI + 组合探测链。

### 1.1 supports/locate 两段式 SPI [🔴 P1] [时间无关模式]
- **来源**：KP-507（ZoneLocator + AbstractZoneLocator）/ KP-509（DefaultZoneLocator）
- **机制**：**SPI 契约**（supports(Environment) 声明支持性 :20 + locate(Environment) 定位 :27——先探测后定位）；抽象基座（BeanNameAware + Ordered :15）；**兜底定位器**（supports 恒 true + order=20——保证探测链总有结果，属性缺省用 defaultZone——Eureka 兼容）
- **对比**：官方 SCL 单属性 vs 多源探测 SPI
- **my-xhs**：该用没用（真实差距）——my-xhs 无 SPI（系统属性+Nacos fallback 单通道）；场景单一可接受

### 1.2 组合定位器（排序+缓存+fast-fail+系统属性回写）[🔴 P1] [时间无关模式]
- **来源**：KP-508（CompositeZoneLocator）
- **机制**：**AnnotationAwareOrderComparator 排序**（:34）+ **volatile 缓存**（:29/:51——一次性定位后直返）+ **fast-fail 两种容错**（默认降级继续 vs 开启失败即抛 :87-89）+ **系统属性回写**（:98——`System.setProperty(CURRENT_ZONE_PROPERTY_NAME)`——**Spring↔非 Spring 全局通道**（与 ZoneContext.getCurrentZone :233 闭环——KP-501 双通道设计自洽））
- **my-xhs**：该用没用（真实差距）——无组合定位器（无多源需求）

### 1.3 云元数据探测源（AWS 家族）[🔴 P1] [时间无关模式]
- **来源**：KP-517（Ec2/ECS File/ECS V4 三定位器）
- **机制**：**环境变量存在性 = 平台判定**（supports = containsProperty(ECS_CONTAINER_METADATA_FILE/URI_V4) :45-46——非 ECS 环境自动跳过）；**IMDS 端点**（169.254.169.254 链路本地 :24 + HttpUtils.doGet）；**order 链**（ECS File=5 → V4=10 → EC2=15 → Default=20——容器内环境优先）；JSON 解析（ObjectMapper + AvailabilityZone 字段 :37）
- **对比**：国内替代——K8s Downward API/节点标签、各云厂商 metadata
- **my-xhs**：该用没用（真实差距）——无云探测族；Nacos 声明式替代

---

## 二、ZoneContext 全局状态（非 Spring 可达）[分布式问题]

> **核心命题**：Zone 信息要被**非 Spring 代码**访问（Ribbon Filter/Redis/MyBatis/静态工具）——全局单例 + JavaBeans 事件（非 Spring 事件）。

### 2.1 全局单例 + 保守默认值 + 双事件桥接 [🔴 P1] [时间无关模式]
- **来源**：KP-501（ZoneContext）/ KP-510（ZoneContextChangedListener）
- **机制**：**静态单例**（:34 + get()）+ **7 volatile 属性**（:36-48——enabled=true/preferenceEnabled=**false**（安全第一）/readyPercentage=**100**（极保守）/minAvailable=5/disabledZone=null）+ **PropertyChangeSupport 值变才触发**（:195-204——避免重复事件）+ **静态 getCurrentZone 读系统属性**（:233）
- **桥接**：ZoneContextChangedListener——**类名探测多容器**（resolveClass 检测 EnvironmentChangeEvent/ApplicationStartedEvent :72-82——spring 模块无 boot/cloud 依赖支持三者）+ **临时监听器收集变更**（add/remove PropertyChangeListener :147/:157——只上报真实变化）+ 策略映射表（属性名→Consumer :125-133）+ **ORIGINAL_ZONE 回退**（:176——配置设 originalZone 重新定位）+ 属性删除=重置（:230-232）
- **生态呼应**：JavaBeans 事件 ↔ Spring 事件双系统桥接（microsphere-spring KP-214 双钩模式的跨事件模型版）
- **my-xhs**：已用（简化消费）+ 不该用（复杂桥接）——DynamicDataSource:88/:101 原生 PropertyChangeListener 切换数据源；无容器探测/Spring 事件桥接

### 2.2 单例 Bean 化 + 双通道装配 [🔴 P1] [时间无关模式]
- **来源**：KP-512（ZoneAutoConfiguration）/ KP-513（条件注解家族）
- **机制**：**单例→Bean 适配**（@Bean 返回 ZoneContext.get() :39——全局与容器统一）；**ZoneLocator 双通道收集**（SpringFactories loadFactories :47 + Bean 收集 :50——第三方定位器零配置接入 + 排序组装 Composite）；**条件组合注解**（可用 ⊃ 启用——@ConditionalOnClass 嵌套 @ConditionalOnProperty(matchIfMissing=true)）
- **my-xhs**：已用——ZoneContextAutoConfiguration:26-46 单例 Bean 化（@ConfigurationProperties 绑定）

---

## 三、区域优先路由（多级保护）[分布式问题]

> **核心命题**：官方 SCL 仅"同区过滤"；microsphere 叠加**禁用区/就绪率/最小可用**三重保护（REQ 表实证）。

### 3.1 多级保护过滤算法 [🔴 P1] [时间无关模式]
- **来源**：KP-503（ZonePreferenceFilter）
- **机制**：**七级流程**（null/单元素 → 总开关 → 偏好开关（默认关）→ defaultZone 忽略（大小写不敏感）→ 禁用区剔除（≤1 回退防剔光 :74-77）→ **就绪率保护**（zoneCount*100/entitiesSize 整数百分比 :165——zone 元数据缺失的上游不算 ready）→ **最小可用保护**（<阈值回退全量））
- **与官方对照**：SCL 只按 zone metadata 过滤——无三重保护
- **my-xhs**：已用——ZonePreferenceFilter 移植（三重保护全保留 + 完整测试）

### 3.2 LoadBalancer 优化版 Supplier + 装饰链 [🔴 P1] [时间无关模式]
- **来源**：KP-516（CustomizedLoadBalancer* + ZonePreferenceServiceInstanceListSupplier）
- **机制**：**官方同名重写**（extends DelegatingServiceInstanceListSupplier :34——与官方 `org.springframework.cloud.loadbalancer.core.ZonePreferenceServiceInstanceListSupplier` 同名——IDE import 混淆风险）；**builder 装饰链**（withDiscoveryClient().withCaching().with(ZonePreference... ) :64-67——官方扩展点）；**双栈配置**（Reactive/Blocking @Order 193827465/466 :54-72）；**客户端级条件**（LoadBalancerEnvironmentPropertyUtils "configurations"=optimized-zone-preference :97-98）；**默认关闭**（customized=true 才装配——不干扰官方）
- **my-xhs**：已用（已深读）——ZonePreferenceServiceInstanceListSupplier 逐行同构移植 + ZoneLoadBalancerConfiguration（单级装配/开关合并/双内部类互斥——R1 条件矛盾同样存在）

### 3.3 Ribbon 集成（过时适配）[🔴 P2] [过时→替代]
- **来源**：KP-518（netflix 族）
- **机制**：Ribbon ServerListFilter 集成（ZonePreferenceServerListFilter 委托过滤）+ **Server 包装 ServiceInstance**（super(scheme,host,port) + setZone :38-41——跨生态桥接）+ DiscoveryClientServerList（发现结果→Ribbon Server 流式映射）+ Eureka PreRegistrationHandler 原生钩子附加 zone（beforeRegistration :30-33）
- **过时**：Ribbon 2.7.18 已过时（Spring Cloud LoadBalancer 替代）——模块整体过时但模式可借鉴
- **my-xhs**：不该用——Ribbon 过时；Eureka metadata 机制可借鉴

---

## 四、注册元数据与消费闭环 [分布式问题]

> **核心命题**：**生产者（注册前附加）→ 消费者（metadata 解析）** 同一属性名契约。

### 4.1 Zone 元数据附加（双框架双实现）[🔴 P1] [时间无关模式]
- **来源**：KP-504（ZoneAttachmentHandler）/ KP-514（ZoneCloudAutoConfiguration + ZoneAttachmentListener）
- **机制**：**attachZone**（:28-42——不可变 Map 防御 try-catch :32-38 + isNotBlank 守卫 :30）；**cloud 侧**（ApplicationListener\<RegistrationPreRegisteredEvent> :23——05 仓库注册事件体系消费——@ConditionalOnBean(EventPublishingRegistrationAspect) 跨仓库条件装配 :49 + @AutoConfigureAfter 值/名混合 :35-42）；**netflix 侧**（Eureka PreRegistrationHandler 原生钩子）——**双路径并存幂等**（历史 17-01 实证）
- **时序风险**：定位失败→区域空→不写 metadata→就绪率受影响（历史 17-07 实证）
- **my-xhs**：该用没用（被替代）——无 attachZone；Nacos 配置声明（spring.cloud.nacos.discovery.metadata.zone）

### 4.2 实例元数据解析（消费者）[🟡 P2] [时间无关模式]
- **来源**：KP-515（CloudServerZoneResolver）/ KP-502（ZoneResolver 泛型）/ KP-506（HttpUtils）
- **机制**：**单例无状态解析器**（INSTANCE :21——metadata.get(ZONE_PROPERTY_NAME) :27——与生产端同名契约闭环）；泛型 ZoneResolver 多实体适配（ServiceInstance/InstanceInfo/Server）
- **my-xhs**：已用——ServiceInstanceZoneResolver（metadata 读 zone）

---

## 五、配套与工程面 [工程问题]

### 5.1 常量/工具/条件注解 [🟢 P3] [时间无关模式]
- **来源**：KP-505（ZoneConstants）/ KP-511（ZoneUtils + 事件载体）/ KP-513
- **机制**：**前缀分层属性族**（zone.preference.upstream.disabled-zone 三段式）+ **拼写错误实证**（PREFERENCE_FILER :108——FILER 应为 FILTER——API 稳定约束）；Bean 名常量 + 按名取 Bean；ZoneContextChangedEvent extends ApplicationContextEvent + unmodifiableList 载荷（:36）
- **my-xhs**：已用——ZoneConstants 移植 + ZoneProperties（@ConfigurationProperties 嵌套替代常量接口）

### 5.2 JDK 原生 HTTP（过时）[🟢 P3] [过时→替代]
- **来源**：KP-506（HttpUtils）
- **机制**：HttpURLConnection 双超时 + try-with-resources + disconnect（:40-47）；非 HTTP 协议静默 null（:38）；info 级日志打印响应（:44——生产隐患）
- **替代**：JDK11 java.net.http.HttpClient / Spring WebClient
- **my-xhs**：不该用——Spring RestTemplate/WebClient 覆盖

---

## 六、my-xhs 落地对照（2026-08-13 实证——本仓库最大落地案例）

> **核心命题**：my-xhs 已把 microsphere-multiactive 的 zone 机制**整体移植并现代化改写**，并**自主扩展**了微球没有的 Redis 拦截族与 DataSource 切换——本仓库是"提取 → 落地"闭环的最佳实证。

### 6.1 移植清单（已用 10 KP）[实证]

| 微球（本仓库） | my-xhs 移植 | 差异 |
|----------------|------------|------|
| ZoneContext（KP-501） | `com/myxhs/common/zone/ZoneContext.java` | diff 实证逐字段对应（7 属性/isPropertyChanged/值变才触发）；@Slf4j/私有构造/trim 现代化改写 |
| ZoneResolver（KP-502） | `zone/ZoneResolver.java` + `zone/loadbalancer/ServiceInstanceZoneResolver.java` | 无多接入源抽象（Nacos 单源） |
| ZonePreferenceFilter（KP-503） | `zone/ZonePreferenceFilter.java` | 三重保护全保留 + 完整测试（ZonePreferenceFilterTest） |
| ZoneConstants（KP-505） | `zone/ZoneConstants.java` + `zone/ZoneProperties.java` | @ConfigurationProperties 嵌套替代常量接口 |
| DefaultZoneLocator 语义（KP-509） | ZoneContextAutoConfiguration:41-42 | 系统属性→Nacos metadata→defaultZone 兜底（无独立类） |
| 单例 Bean 化（KP-512） | `zone/ZoneContextAutoConfiguration.java:26-46` | @ConfigurationProperties 绑定替代 SpringFactories 双通道 |
| 条件注解（KP-513） | ZoneContextAutoConfiguration:17 | @ConditionalOnProperty matchIfMissing=true（单层） |
| 实例元数据解析（KP-515） | `zone/loadbalancer/ServiceInstanceZoneResolver.java` | 同构 |
| LoadBalancer Supplier（KP-516） | `zone/loadbalancer/ZonePreferenceServiceInstanceListSupplier.java` + `ZoneLoadBalancerConfiguration.java` | 逐行同构移植（:19-38）+ 单级装配（无子上下文）/开关合并（preference.enabled）/双内部类同名互斥——**R1 条件矛盾同样存在**；ServiceInstanceZoneResolver 补 null 防御 |
| 事件消费（KP-510 消费侧） | `zone/datasource/DynamicDataSource.java:88/:101` | **原生 PropertyChangeListener 切换数据源**——比微球 Spring 事件桥接更直接 |

### 6.2 真实差距（该用没用 5 KP）[实证]

| 差距 | 现状 | 判定 |
|------|------|------|
| supports/locate 多源探测 SPI（KP-507） | 系统属性+Nacos metadata 单通道 | 场景单一可接受 |
| 组合定位器 + fast-fail（KP-508） | 无（无多源需求） | 场景单一可接受 |
| 注册前元数据附加（KP-504/514） | **被 Nacos 配置声明式替代**（spring.cloud.nacos.discovery.metadata.zone） | 无需注册前钩子 |
| 云元数据探测族（KP-517） | 无（国内 Nacos 场景） | K8s Downward API/节点标签为现代替代 |
| 双事件桥接复杂层（KP-510） | 无容器探测/Spring 事件发布 | 场景不需要（不该用） |

### 6.3 my-xhs 自主增量（微球没有的）[实证——多活知识延伸]

- **`zone/redis/` 族 7 文件**：RedisInterceptorAutoConfiguration / RedisCommandEvent / EventPublishingRedisCommandInterceptor / RedisMethodContext / RedisMethodInterceptor / InterceptingRedisConnectionInvocationHandler / RedisTemplateWrapper——**多活 Redis 命令拦截与事件化**（Zone 感知的 Redis 可观测）
- **`zone/datasource/DynamicDataSource`**：**zone 变更 → 数据源切换**——与 **microsphere-dynamic 仓库（下一提取站）强关联**——多活 Zone 的存储侧联动
- **`zone/loadbalancer/ZoneLoadBalancerConfiguration`**：LoadBalancer 定制装配的 my-xhs 版

> **教学启示**：本仓库证明"提取 → 重写 → 上真线"闭环成立——my-xhs 的移植不是复制：现代化改写（@ConfigurationProperties/@Slf4j/原生监听）+ 场景化瘦身（单源 Nacos）+ 自主扩展（Redis/DataSource）——**知识本体的三种处理方式实证**。

---

## 覆盖核对（16/16 + my-xhs 对照）

| 维度 | 覆盖 KP | 数 |
|------|---------|:--:|
| 一、Zone 自动发现 | 507,508,509,517 | 4 |
| 二、ZoneContext 全局状态 | 501,510,512,513 | 4 |
| 三、区域优先路由 | 503,516,518 | 3 |
| 四、注册元数据闭环 | 502,504,506,514,515 | 5 |
| 五、配套与工程面 | 505,511 | 2 |
| 六、my-xhs 落地对照 | 移植 10 / 差距 5 / 增量 3 族（非独立 KP） | — |

**去重后唯一 KP**：501-518 全部 = **16/16 ✓**
**无孤儿 KP** ✓
