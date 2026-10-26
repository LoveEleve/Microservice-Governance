# microsphere-multiactive 知识点提取

> 源码：`/data/workspace/java-training-camp/cloud-native-code/stage-4/microsphere-multiactive`（依赖链第 6 站，spring-cloud 之后；stage-4 多活课程源码侧，31 生产文件）
> 提取时间：2026-08-13（批 1：commons 6；批 2：spring 7；批 3：spring-boot 3 + spring-cloud 6；批 4：aws 3 + netflix 6）
> 状态：批 1 已提取；MCP 索引已建（982 节点/2631 边）
> 关联：stage-4 课程提取（多活架构）强关联——**Zone 自动发现/全局上下文/Zone 优先路由**
> 历史交叉验证：`microsphere-analysis/17-microsphere-multiactive-analysis/17-REQ-requirements-spec.md`（REQ-001~004 + 缺陷表）

## 一、仓库定位

**多活 Zone-Aware 部署框架**——应用自动发现部署 Zone + 同区优先路由 + 跨区回退。模块：commons（6 核心抽象）/aws（3 云发现）/netflix（6 Ribbon/Eureka 集成）/spring（7 定位器与事件）/spring-boot（3 自动装配）/spring-cloud（6 LoadBalancer 集成）。核心维度：[分布式问题]（区域路由）+ [工程问题]（Spring/Cloud 集成）。

**依赖链**：commons → spring → spring-boot → spring-cloud → aws/netflix（pom 实证：spring 依赖 commons+microsphere-spring-context；boot 依赖 spring+microsphere-spring-boot-core；cloud 依赖 boot+microsphere-spring-cloud-commons+spring-cloud-loadbalancer；aws/netflix 依赖 cloud）

## 前置条件清单

读者需先掌握：1. 前五仓库全部知识点 2. Spring Cloud LoadBalancer（ZonePreferenceServiceInstanceListSupplier——官方对照）3. Ribbon ServerListFilter（netflix 集成对照）4. stage-4 课程（多活——本仓库是其源码侧）
未达前置者，先补：前五仓库 outline + Spring Cloud LoadBalancer 知识（**本地无 spring-cloud-loadbalancer 源码——官方对照断言基于 API 语义，置信度 Medium**）

## 掌握度

目标读者：中级偏上（读源码多、Spring 熟——HANDOVER 画像）
讲解策略：与官方 SCL ZonePreferenceServiceInstanceListSupplier 对照 + 云厂商元数据（AWS）机制

---

## 二、逐文件映射 + 原子记录

### 包: `io.microsphere.multiple.active.zone`（批 1：commons 6 文件）

#### KP-501 `ZoneContext` 非 Spring 全局单例 + 属性变更事件（ZoneContext.java:30-234）

- **维度**：[分布式问题]（区域上下文）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（全局上下文+监听器） | **置信度**：High
- **前置**：单例模式、volatile 可见性、java.beans.PropertyChangeSupport（JavaBeans 事件模型）、Spring Environment（对比对象）
- **需求**：**Zone 信息要被非 Spring 代码访问**——Ribbon ServerListFilter/Redis 连接工厂/MyBatis 拦截器/静态工具类拿不到 BeanFactory——需要一个全局可达的 Zone 状态持有者（历史 REQ-002 实证）
- **自主实现**：若我来做——Spring Environment 不够（非 Spring 代码用不了）；全局单例 + 字段 volatile + 变更监听器（PropertyChangeSupport 或自写 listener 接口）是标准方案；现代替代可用系统属性 + 静态持有（同 Java System 属性模式）
- **参考实现**：**静态单例**（:34——`private static final ZoneContext instance = new ZoneContext()` + get() :223-225）；**7 个 volatile 属性**（:36-48——enabled=true / zone="defaultZone"（Eureka 兼容 :29） / preferenceEnabled=**false**（安全第一——开启后流量分布改变） / preferenceFilterOrder=10 / preferenceUpstreamZoneReadyPercentage=**100**（极保守——1 个实例无 zone 信息就 99%<100% 不应用偏好） / preferenceUpstreamSameZoneMinAvailable=5 / preferenceUpstreamDisabledZone=null）；**JavaBeans 事件**（:50 PropertyChangeSupport + isPropertyChanged :195-204——**值真正变化才 firePropertyChange**（避免重复设置产生无用事件）——非 Spring ApplicationEvent）；**静态 getCurrentZone**（:232-234——**读 System.getProperty(CURRENT_ZONE_PROPERTY_NAME)——不走单例字段**（注释意图：使 ZoneLocator 定位结果全局可见））；**enable()/reset()**（:174-193）
- **对比取舍**：**知识增量**：①**非 Spring 全局可达的配置单例**（vs Spring Environment 只能在 Bean 内用——JavaBeans 事件模型的适用场景）；②**volatile 属性族**（多配置项可见性）；③**保守默认值设计**（preferenceEnabled=false + readyPercentage=100——安全第一 vs 功能默认开）；④**状态双通道设计**：instance 字段（setZone :58-62 写字段 + 事件）vs System 属性（getCurrentZone :233 读属性）——**两套状态并存**：setZone 不写系统属性、getCurrentZone 不读 instance 字段——`ZoneContext.get().setZone("a")` 后 `getCurrentZone()` 仍返回默认——**潜在不一致**（设计注释意图是 ZoneLocator 负责写系统属性，但 API 表面存在双通道分裂）；⑤**事件只在不变化时静默**（:200-202 trace 日志）
- **测试佐证**：ZoneContextTest 全量断言——单例（:45-47）、7 默认值（:51-58）、**同值不触发事件**（:71-77 events.size()==0——PropertyChangeSupport 语义实证）、空白 trim（:88-90）、逗号拆分去空白（:136-138）
- **my-xhs**：**已用（实证 2026-08-13）**——my-xhs 已整体移植本机制：`my-xhs-common/.../com/myxhs/common/zone/ZoneContext.java`（**结构逐字段对应**——7 属性/isPropertyChanged/值变才触发 全同，diff 实证仅现代化改写：@Slf4j/私有构造/`String.trim` 替代 trimAllWhitespace）+ `ZoneContextAutoConfiguration.java:27-46`（**单例 Bean 化**（get() :28）+ ZoneProperties 绑定）+ `ZoneProperties.java`（**@ConfigurationProperties(prefix="myxhs.availability.zone") 嵌套结构替代微球常量接口**）；**差异**：my-xhs zone 来源 = `System.getProperty(CURRENT_ZONE_PROPERTY_NAME)` fallback `spring.cloud.nacos.discovery.metadata.zone`（:41-42——**Nacos metadata 声明式**，无定位器链）

#### KP-502 `ZoneResolver` 泛型 Zone 解析 SPI（ZoneResolver.java:11-24）

- **维度**：[分布式问题]（区域解析）| **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式]（泛型 SPI） | **置信度**：High
- **前置**：函数式接口（Function<E,R>）、泛型、SPI 设计（supports/locate 模式见 spring 批）
- **需求**：**从任意实体解析 zone**——统一 Zone 解析抽象（实体可能是 ServiceInstance/Server/Ribbon Server 等，由各集成层提供实现）
- **参考实现**：**函数式接口**（:11——`interface ZoneResolver<E> extends Function<E, String>`——**用 JDK Function 语义做解析契约**）；**apply 委托 resolve**（:13-15——default 方法桥接 Function）；**null 可返回**（:21 注释——"null if can't be resolved"——解析失败返回 null 的契约）
- **对比取舍**：**知识增量**：①**函数式接口 + 领域方法双入口**（Function.apply 与领域语义 resolve——Stream/lambda 场景可用 apply）；②**null 语义契约**（解析失败=null——配合 ZonePreferenceFilter 的 zoneCount 统计）
- **测试佐证**：ZoneResolverTest——apply 委托 resolve 实证（:14-18）+ null 实体返回 null（:22-24）
- **my-xhs**：**已用（实证）**——`my-xhs-common/.../zone/ZoneResolver.java`（同名移植）+ `zone/loadbalancer/ServiceInstanceZoneResolver.java`（ServiceInstance 专用——同 KP-515）；**无多接入源抽象**（my-xhs 仅 Nacos metadata 单源——多源探测仍是差距）

#### KP-503 `ZonePreferenceFilter` 多级保护的同区过滤算法（ZonePreferenceFilter.java:23-185）

- **维度**：[分布式问题]（区域路由）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（区域优先路由） | **置信度**：High
- **前置**：Ribbon ZonePreferenceServerListFilter（官方对照）、Nginx 区域路由、整型百分比计算
- **需求**：**同区实例优先 + 跨区回退**，且带精细保护策略——禁用区/就绪率/同区最小可用数（历史 REQ-003 的 commons 泛型抽象）
- **自主实现**：若我来做——过滤链：启用开关 → 偏好开关 → zone 有效性 → 禁用区剔除 → 同区筛选 → 就绪率/最小可用阈值回退；注意返回**原列表引用 vs 新列表**语义要明确（本实现 disabled 分支返回新 LinkedList，其余返回原引用）
- **参考实现**：**过滤流程**（filter :36-127——七级保护）：①null/空/单元素直接返回（:38-43）；②zoneContext 未启用返回原列表（:46-50）；③preference 未启用返回（:52-56——**默认关闭，需显式启用**）；④isIgnored（:59-63——blank 或 defaultZone **大小写不敏感** :182-184——Eureka 默认区名不参与偏好）；⑤**禁用区过滤**（:67-81——filterDisabledZone :133-151 按逗号拆分禁用区集合 :136，过滤后 **≤1 个回退原列表**（:74-77——避免全量被剔除））；⑥**上游就绪率保护**（:102-107——`zoneCount*100/entitiesSize` 整数百分比 :165，**低于阈值整体回退**——zoneCount 只统计 resolveZone != null 的 :93-94——zone 元数据缺失的上游视为不ready）；⑦**同区最小可用数保护**（:111-118——同区数 < 阈值回退全量）；命中返回 sameZoneEntities（:121），未命中返回 targetEntities（:126）
- **对比取舍**：**知识增量**：①**区域优先路由的多级保护链**（vs 官方 SCL ZonePreferenceServiceInstanceListSupplier 仅按 zone 过滤——microsphere 叠加禁用区/就绪率/最小可用三重保护——REQ 表"微球 vs SCL"实证）；②**"全量剔除预防"设计**（disabled 过滤后 ≤1 回退——防故障区剔光导致无实例）；③**就绪率整数除法**（:165——`zoneCount*100/entitiesSize`——整数截断（如 2/3=66%）——低实例数时保护更激进）；④**返回引用语义不统一**（disabled 分支新列表 vs 其余原引用——调用方不可假设可变性）；⑤**null 元素防御**（:174——entity null 时 zone=null 不计数）——**注**：官方对照断言（"官方仅按 zone 过滤"）基于 API 语义——本地无 SCL 源码（见 review ⑤b）——置信度 Medium
- **测试佐证**：ZonePreferenceFilterTest 15 断言——null/空/单元素（:65-77）、disabled/preference 关闭（:84-99）、defaultZone 大小写不敏感忽略（:102-122）、同区命中（:125-133）、无同区回退全量（:137）、**就绪率不达标回退**（:149-158）、**最小可用阈值回退**（:164）、**禁用区过滤**（:180-204）、**禁用区剔光回退**（:208）、null 实体（:228-251）
- **my-xhs**：**已用（实证）**——`my-xhs-common/.../zone/ZonePreferenceFilter.java`（**同结构移植**：filterDisabledZone/isDisabledZone/isUpstreamZoneNotReady/resolveZone 方法族全同 + 完整测试 ZonePreferenceFilterTest（:126-142 disabledZone、:210-219 mixed entities））；**差异**：my-xhs 保留全部三重保护（禁用区/就绪率/最小可用）——无瘦身

#### KP-504 `ZoneAttachmentHandler` Zone 元数据附加（ZoneAttachmentHandler.java:18-43）

- **维度**：[分布式问题]（注册元数据）| **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：Registration.metadata（Spring Cloud 服务注册元数据）、不可变 Map 防御
- **需求**：**发现 Zone 后写入服务注册元数据** `metadata["zone"]`——使下游能感知（历史 REQ-004）
- **参考实现**：**attachZone(metadata)**（:28-42——zoneContext.getZone() 非空才写 :30 + **不可变 Map 防御**（:32-38——try-catch UnsupportedOperationException——注释明示"如果 metadata 不可变将抛异常"——**防御式写入**（注册元数据可能被框架包装成不可变）））；失败 warn 降级（:37）
- **对比取舍**：**知识增量**：①**防御式写入**（不可变 Map 的 try-catch——先写后捕 vs 先查后写（ConcurrentModification 场景））；②**元数据契约**（zone 属性名 ZONE_PROPERTY_NAME——与 ZonePreferenceFilter 消费端一致——**生产者/消费者同名契约**）
- **my-xhs**：**该用没用（部分被替代）**——my-xhs **无 ZoneAttachmentHandler**（zone 包无 handler 实证）——但用 `spring.cloud.nacos.discovery.metadata.zone` **配置声明式**替代（ZoneContextAutoConfiguration:42——Nacos 注册时自带 metadata——**无需注册前附加钩子**）——注册前附加机制对 my-xhs 非必要

#### KP-505 `ZoneConstants` 常量接口 + 属性元数据注解（ZoneConstants.java:15-241）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟢 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：@ConfigurationProperty 注解（microsphere 自家——microsphere-java 批次）、常量接口模式、属性名前缀体系
- **需求**：**属性名/默认值统一管理**——microsphere.availability.zone.* 属性族（前缀分层：zone → preference → filter/upstream → locator）
- **参考实现**：**常量接口**（:15——interface 常量 + @ConfigurationProperty 元数据（:42-46/:69-74/:96-101/:123-128/:150-155/:172-177/:189-192/:214-219/:234-239——**属性名/类型/默认值/来源声明一体化**——编译期生成配置元数据（呼应 java KP-146a 配置元数据三阶段闭环））；**DEFAULT_ZONE 兼容 Eureka**（:29——`@see EurekaClientConfigBean#DEFAULT_ZONE`——**跨框架兼容注释实证**（默认区名对齐））；**ORIGINAL_ZONE**（:37——定位回退用保留值）；**默认值集中**（:59-64/:86-91/:113-118/:140-145/:162-167/:184/:204-209/:224-229——parseBoolean/parseInt 静态转换）
- **对比取舍**：**知识增量**：①**前缀分层的属性族**（zone.preference.upstream.disabled-zone 三段式）；②**拼写错误实证**：`PREFERENCE_FILER_PROPERTY_NAME_PREFIX`（:108——**FILER 应为 FILTER**——接口字段名拼错——API 稳定约束（字段名是公开常量，改名破坏兼容）——同前仓库 StacKTrace/Pattens 先例）；③**配置元数据注解**（@ConfigurationProperty——microsphere 自家注解体系——配置文件→编译期 JSON→运行期加载闭环）
- **my-xhs**：**已用（实证）**——`my-xhs-common/.../zone/ZoneConstants.java`（同名移植）+ **现代化替代**：`ZoneProperties.java`（@ConfigurationProperties 嵌套——enabled/preference/filter/upstream 四层结构与微球常量前缀逐层对应——**微球常量接口 → Boot 标准绑定**（此处"不该用"判定修正：my-xhs 已用 @ConfigurationProperties 替代，微球方案确不该用））

#### KP-506 `HttpUtils` JDK 原生 HTTP GET（HttpUtils.java:22-52）

- **维度**：[工程问题] | **权重**：[边缘] | **深度**：🟢 | **优先级**：P3 | **过时**：[过时→替代]（HttpURLConnection → java.net.http.HttpClient JDK11） | **置信度**：High
- **前置**：HttpURLConnection、try-with-resources、字符集
- **需求**：**简单 HTTP GET**——AWS metadata endpoint 拉取（不引第三方 HTTP 库）
- **参考实现**：**doGet(url, timeout)**（:34-51——blank URL 返回 null :36；**HttpURLConnection 专属处理**（:38——非 HTTP 协议返回 null）；connect/read 双超时 :40-41；**try-with-resources + disconnect**（:42-47——finally 断开连接）；IOUtils.copyToString 读流（:43））；**作者 Walklown**（:19——非 Mercy 作者头——外部贡献实证）
- **对比取舍**：**知识增量**：①**JDK 原生 HTTP vs HttpClient**（:38 的 instanceof HttpURLConnection 分支——JDK11 java.net.http.HttpClient 是现代替代（异步/HTTP2）；②**非 HTTP 协议静默 null**（:48——file:/jar: 协议返回 null 不报错）；③**日志记录响应内容**（:44——info 级别打印整个响应——**生产隐患：metadata 响应可能含敏感信息**）
- **测试佐证**：HttpUtilsTest——null/空/blank URL 返回 null（:22-36）、非 HTTP 协议 null（:40-48）、**临时 HTTP Server 端到端**（:52-69——断言响应内容一致——测试自带 mini server 实证）
- **my-xhs**：**不该用**——my-xhs 用 Spring RestTemplate/WebClient；JDK11 HttpClient 可替代单次 GET 场景

### 包: `io.microsphere.multiple.active.zone.spring`（批 2：spring 7 文件）

#### KP-507 `ZoneLocator` SPI + `AbstractZoneLocator` 基座（ZoneLocator.java:11-29 + AbstractZoneLocator.java:15-46）

- **维度**：[分布式问题]（区域发现）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（supports/locate SPI） | **置信度**：High
- **前置**：Spring Environment、supports/locate 探测模式（类似 Strategy 选择）、BeanNameAware/Ordered
- **需求**：**Zone 自动发现 SPI**——`supports(Environment)` + `locate(Environment)` 两段式探测（历史 REQ-001——K8s/AWS 环境 Zone 是 Pod 部署时分配的，运维不应手写）
- **参考实现**：**SPI 契约**（:20 supports——是否支持当前环境探测；:27 locate——定位 zone——**两段式探测**（先声明支持性再定位——多实现共存时的选择机制））；**抽象基座**（AbstractZoneLocator :15——implements ZoneLocator + **BeanNameAware + Ordered**（:15——Bean 名可观测 + 排序支持）；构造注入 order :23-25；setBeanName final :28-30）
- **对比取舍**：**知识增量**：①**supports/locate 两段式 SPI**（vs Spring 直接 getBean 单选——支持性探测+定位分离，多实现组合（Composite）的前提）；②**Ordered 排序基座**（配合 CompositeZoneLocator 的 AnnotationAwareOrderComparator——**顺序敏感的多源探测**）
- **my-xhs**：**该用没用（真实差距）**——my-xhs 无 supports/locate SPI（用 System.getProperty + Nacos metadata fallback 单通道替代——ZoneContextAutoConfiguration:41-42）——多源探测是差距；但 my-xhs 场景单一（Nacos）简化可接受

#### KP-508 `CompositeZoneLocator` 组合定位器（排序+缓存+fast-fail+系统属性回写）（CompositeZoneLocator.java:23-112）

- **维度**：[分布式问题]（区域发现）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（组合定位器） | **置信度**：High
- **前置**：AnnotationAwareOrderComparator、fast-fail 容错模式、volatile 缓存、系统属性（System.getProperty 全局可见性）
- **需求**：**多 ZoneLocator 组合**——按 order 排序探测，第一个定位成功生效（历史 REQ-001"3 个内置实现 + 第一个 supports=true 生效"）
- **参考实现**：**构造排序**（:31-35——Assert.notNull + **AnnotationAwareOrderComparator.sort**（:34——按 order 排序探测链））；**supports 任一**（:38-46——任一支持即 true（组合的存在性））；**locate 主流程**（:49-102——**volatile zone 缓存**（:29/:51——首次定位后直接返回——**一次性定位 + 缓存**）；**fast-fail 开关**（:57/:104-106——读 `microsphere.availability.zone.locator.fast-fail`（默认 false）——定位失败或异常时**直接抛 IllegalStateException**（:87-89——失败即启动失败）；非 fast-fail：**异常跳过继续探测**（:78-84 catch Throwable——单源故障不阻断）；**成功回写系统属性**（:97-99——`System.setProperty(CURRENT_ZONE_PROPERTY_NAME, zone)`——**与 ZoneContext.getCurrentZone（KP-501 :233 读系统属性）闭环**——非 Spring 全局可见）
- **对比取舍**：**知识增量**：①**组合 + 排序 + 缓存**（多源探测的完整设计）；②**fast-fail 两种容错语义**（默认"失败降级继续" vs 开启"失败即抛"——**探测失败的策略选择**）；③**catch Throwable 吞异常降级**（:78——单源崩溃不阻断整体定位——但 Error 也吞）；④**系统属性做全局通道**（:98——Spring 世界 ↔ 非 Spring 世界桥接——呼应 KP-501 双通道设计——**设计自洽**（CompositeZoneLocator 写、getCurrentZone 读）——但 zone 字段（setZone）通道仍是分裂的）
- **测试佐证**：CompositeZoneLocatorTest 全断言——null 构造抛 IllegalArgumentException（:112-113）、supports 任一/全否（:117-129）、**缓存**（:142-150——两次 locate 同值）、无定位返回 null（:154-159）、跳过不支持（:163-168）、**fast-fail null 抛 IllegalStateException**（:172-176）、**fast-fail 异常抛**（:180-184）、**异常无 fast-fail 降级**（:188-194）
- **my-xhs**：**该用没用（真实差距）**——无 Composite 组合定位器（无多源探测需求）；系统属性回写通道 my-xhs 有（ZoneContextAutoConfiguration:41 读系统属性——生产者缺位）

#### KP-509 `DefaultZoneLocator` 属性兜底定位器（DefaultZoneLocator.java:15-40）

- **维度**：[分布式问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：Environment.getProperty、兜底策略（fallback）
- **需求**：**配置属性定位 + defaultZone 兜底**——从 `microsphere.availability.zone` 读，无则 defaultZone
- **参考实现**：**永支持**（:24-26——supports 恒 true——**探测链的最终兜底**（order=20 :17——排在云定位器之后，总是能定位）；**属性优先 + 兜底**（:30-37——getProperty(ZONE_PROPERTY_NAME) 有值用之，无值 DEFAULT_ZONE（"defaultZone"——Eureka 兼容））
- **对比取舍**：**知识增量**：①**兜底定位器模式**（supports 恒 true + 默认值——保证 Composite 总有结果（测试实证无兜底时 null））；②order=20 的定位（默认值定位器排序靠后——**显式配置优先于兜底**）
- **my-xhs**：**已用（简化版）**——my-xhs ZoneContextAutoConfiguration:41-42 = **简化兜底**（系统属性 → Nacos metadata → "defaultZone"——微球 DefaultZoneLocator 的配置兜底语义，无独立类）

#### KP-510 `ZoneContextChangedListener` 双事件桥接 + 容器探测（ZoneContextChangedListener.java:56-251 + ZoneContextChangedEvent:19-46）

- **维度**：[工程问题]（事件桥接）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（事件桥接/类名探测） | **置信度**：High
- **前置**：SmartApplicationListener（supportsEventType）、java.beans.PropertyChangeEvent、Consumer 策略表、ClassLoader resolveClass（无编译依赖探测）
- **需求**：**配置变更 → ZoneContext 属性更新 → Spring 事件通知**——Spring 属性（EnvironmentChangeEvent/ApplicationStartedEvent/ContextRefreshedEvent 触发）→ 更新 ZoneContext（JavaBeans 事件）→ 汇总发布 ZoneContextChangedEvent（Spring 事件）——**双事件系统桥接**（历史 REQ-002"事件机制：PropertyChangeSupport（Java 标准）"）
- **参考实现**：**容器探测**（:67-82——**类名探测无编译依赖**：`resolveClass("org.springframework.cloud.context.environment.EnvironmentChangeEvent")`——Cloud 环境 → 监听 EnvironmentChangeEvent + ApplicationStartedEvent；Boot 环境 → ApplicationStartedEvent；纯 Spring → ContextRefreshedEvent（supportsEventType :109-118 三分支——**按运行时容器选择监听事件**——spring 模块不依赖 boot/cloud 却能感知））；**属性→处理器策略表**（:84-92 7 属性名列表 + :94/:125-133 `Map<String, Consumer<String>>`——属性名映射处理函数——**策略映射表**）；**临时监听器收集变更**（:139-165——**addPropertyChangeListener 临时注册**（:147）→ 逐属性执行 handler → **finally remove**（:157）→ 收集到的 propertyChangeEvents 非空才 publish（:160-164）——**利用 ZoneContext"值变才 fire"语义只上报真实变化**——JavaBeans 事件 → Spring 事件桥接）；**ORIGINAL_ZONE 回退**（:172-192——配置值="originalZone" 时**重新调用 zoneLocator 定位**（revertOriginalZone :183-192——**探测恢复**（如从手动改回自动发现）））；**ZoneContext 作为 Bean 注入**（:242——`context.getBean(ZoneContext.class)`——**单例类被注册为 Spring Bean**（boot 批 ZoneAutoConfiguration 实证））；**getProperty 默认值处理**（:228-237——属性缺失/删除 → 默认值回写（**删除属性也能触发重置**））
- **对比取舍**：**知识增量**：①**类名探测的多容器适配**（spring 模块无 boot/cloud 依赖却支持三者——**无编译依赖的可选集成探测**（类加载探测 + 静态标志））；②**双事件桥接**（JavaBeans PropertyChangeSupport ↔ Spring ApplicationEvent——**两套事件模型的适配**——ZoneContext 保持非 Spring 纯净，Spring 侧桥接）；③**临时监听器 + finally 移除**（收集式变更探测——不污染常驻监听器）；④**属性删除语义**（:230-232——缺失即回默认——**配置删除 = 重置**）；⑤**缺陷**：`context.getBean(ZoneContext.class)` 硬依赖 ZoneContext Bean 存在（:242——无 Bean 则启动失败——由 boot 自动配置保证）；⑥**缺陷（深度 review 实证）——ORIGINAL_ZONE 运行期二次回退失效**：revertOriginalZone（:183-192）调 `zoneLocator.locate(environment)`（:186）——但 CompositeZoneLocator.locate 有 **volatile 缓存**（CompositeZoneLocator :51——hasText(zone) 直接返回缓存值）——**首次定位后，运行期配置再改回 "originalZone" 时 revert 拿到的是缓存值而非重新探测**——"回退到自动发现"语义只在首次（缓存未建立时）生效——运行期动态改回 originalZone 不重新定位（且系统属性 CURRENT_ZONE_PROPERTY_NAME 也保持旧值——:98 只在 locate 成功时更新）；⑦**缺陷**：EnvironmentChangeEvent 分支下启动事件用 ApplicationStartedEvent（:112——Cloud 环境 Boot 启动后触发一次全量同步——启动期无 PropertySource 变更也要全量刷一遍——测试断言值）
- **测试佐证**：ZoneContextChangedListenerTest——supportsEventType 三分支（:83-88）、ContextRefreshed 触发属性同步（:92-97 zone 从属性、:102-107 enabled、:112-117 preferenceEnabled、:122-127 filterOrder=42）
- **my-xhs**：**已用（简化消费版）+ 不该用（复杂桥接层）**——**消费侧已用**：`zone/datasource/DynamicDataSource.java:88/:101`——**addPropertyChangeListener 原生监听 ZoneContext 变更 → onZoneChanged 切换数据源**（比微球更直接的消费——微球经 Spring 事件桥接，my-xhs 直接用 JavaBeans 监听）；**桥接复杂层不该用**（实证取舍）：my-xhs 无容器探测/临时监听器收集/Spring 事件发布（ZoneContextChangedEvent 无对应物——**场景单一不需要**）

#### KP-511 `ZoneUtils` + 事件载体（ZoneUtils.java:15-39 + ZoneContextChangedEvent:19-46）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟢 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：Bean 名约定、不可变 List
- **需求**：**Bean 名常量 + 获取工具**（zoneContext/zoneLocator 固定 Bean 名——按名取 Bean）+ 事件载体
- **参考实现**：**Bean 名常量**（:20/:25——`ZONE_CONTEXT_BEAN_NAME="zoneContext"`/`ZONE_LOCATOR_BEAN_NAME="zoneLocator"`）；**按名取 Bean**（:32-38——getZoneContext/getZoneLocator——ConfigurableListableBeanFactory.getBean(名, 类型)）；**事件载体**（ZoneContextChangedEvent extends **ApplicationContextEvent**（:19——非裸 ApplicationEvent——携带 context）；构造 unmodifiableList（:36——**不可变事件载荷**））
- **my-xhs**：**不该用**——工具类无独立知识（按类型取 Bean 更优——官方 getBean(Class)）

### 包: `io.microsphere.multiple.active.zone.spring.boot`（批 3a：spring-boot 3 文件）

#### KP-512 `ZoneAutoConfiguration` 自动装配（ZoneAutoConfiguration.java:31-57）

- **维度**：[工程问题]（自动配置）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：@ConditionalOnMissingBean、@Import、SpringFactoriesLoader（微球版）、条件组合注解
- **需求**：**Zone 体系一键装配**——ZoneContext/CompositeZoneLocator 自动注册为 Bean + ZoneLocator 多源加载（Bean + SpringFactories 双通道）
- **参考实现**：**类级条件**（:31-33——@Configuration(proxyBeanMethods=false) + @ConditionalOnAvailabilityZoneAvailable + @Import(ZoneContextChangedListener)——**条件组合注解**（类存在 + 属性启用）+ **事件监听器 import**）；**zoneContext Bean**（:36-40——@Bean(name=ZONE_CONTEXT_BEAN_NAME) + @ConditionalOnMissingBean + **返回 ZoneContext.get() 单例**（:39——**全局单例包装为 Bean**（Spring 与全局共享同一实例——呼应 KP-510 :242 getBean(ZoneContext.class) 与 KP-501 非 Spring 可达设计）））；**zoneLocator Bean**（:42-56——@Primary + @ConditionalOnMissingBean + **双通道收集**（:47 `loadFactories(context, ZoneLocator.class)`——**SpringFactories 加载第三方定位器**（SPI 扩展点——aws/netflix 定位器经 factories 注册）+ :50 Bean 收集——合并后排序 :54 组装 Composite :55）
- **对比取舍**：**知识增量**：①**单例 → Bean 适配器**（静态单例注册为 Bean——全局状态与容器状态统一）；②**ZoneLocator 双通道扩展**（@Bean + SpringFactories——**Bean 定义与 SPI 工厂并存**（第三方模块零配置接入））；③**@Primary 组合**（:42——多 Bean 时的默认选择）
- **测试佐证**：ZoneAutoConfigurationTest（extends AutoConfigurationTest——条件评估双路径：配置类存在（:47-49 ZoneContext/CompositeZoneLocator）+ 全局缺失类（:58-60 ZoneContext/ZoneLocator 缺失时条件不匹配）——**自动配置条件双向验证**）
- **my-xhs**：**已用（实证）**——`ZoneContextAutoConfiguration.java:26-46`——@Bean zoneContext 返回 `ZoneContext.get()`（:28——**单例 Bean 化**——与微球同构）；**差异**：my-xhs 用 @ConfigurationProperties 绑定（微球用 SpringFactories 双通道 + @ConfigurationProperty 注解）——my-xhs 无 ZoneLocator 双通道收集（无多源）

#### KP-513 Zone 条件注解家族（ConditionalOnAvailabilityZoneAvailable.java:39-48 + ConditionalOnAvailabilityZoneEnabled.java:38-43）

- **维度**：[工程问题]（条件注解）| **权重**：[支撑] | **深度**：🟡 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：@ConditionalOnClass/@ConditionalOnProperty、条件注解组合（元注解嵌套）
- **需求**：**Zone 可用/启用两级条件**——类存在（可用）+ 属性开关（启用）
- **参考实现**：**启用条件**（ConditionalOnAvailabilityZoneEnabled :41——@ConditionalOnProperty(ZONE_ENABLED_PROPERTY_NAME, **matchIfMissing=true**——默认启用——缺失属性视为匹配））；**可用条件**（ConditionalOnAvailabilityZoneAvailable :42-46——@ConditionalOnClass(ZoneContext+ZoneLocator 两自家类——依赖完整性探测）+ **元注解嵌套**（:46——可用条件自身携带启用条件——**条件组合**（可用 = 类存在 AND 启用）））
- **对比取舍**：**知识增量**：①**条件组合注解**（可用 ⊃ 启用——嵌套条件注解的语义叠加）；②**matchIfMissing=true 默认启用**（vs ZonePreference 默认关闭——**总开关与偏好开关的默认值策略差异**（KP-501：enabled 默认 true / preferenceEnabled 默认 false））
- **my-xhs**：**已用（实证）**——`ZoneContextAutoConfiguration:17`——@ConditionalOnProperty(prefix="myxhs.availability.zone", name="enabled", **matchIfMissing=true**)——与微球启用条件同语义（默认启用）；**差异**：my-xhs 单层条件（无 @ConditionalOnClass 可用/启用组合——依赖简单无需组合）

### 包: `io.microsphere.multiple.active.zone.spring.cloud`（批 3b：spring-cloud 6 文件）

#### KP-514 `ZoneCloudAutoConfiguration` 云装配 + ZoneAttachmentListener（ZoneCloudAutoConfiguration.java:26-55 + ZoneAttachmentListener.java:23-39）

- **维度**：[分布式问题]（注册元数据）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：microsphere-spring-cloud 注册事件（RegistrationPreRegisteredEvent——05 仓库 KP-405）、AspectJ、@AutoConfigureAfter、Eureka 自动配置
- **需求**：**Cloud 环境 Zone 注册元数据附加**——服务注册前事件 → Zone 写入 Registration.metadata（REQ-004 落地）+ 条件装配（依赖 microsphere-spring-cloud 注册事件切面）
- **参考实现**：**五重条件**（:26-34——@ConditionalOnAvailabilityZoneAvailable + @ConditionalOnAutoServiceRegistrationEnabled（微球自家）+ @ConditionalOnClass（**3 类名**：AspectJ API + 微球 RegistrationPreRegisteredEvent + REGISTRATION_CLASS_NAME（微球 ServiceRegistryConstants 常量 :17））+ @ConditionalOnBean(ZoneContext) + @AutoConfigureAfter（:35-42——**官方 EurekaClientAutoConfiguration + 微球 ServiceRegistryAutoConfiguration**（值/名混合 :37-41）））；**@Import(ZoneAttachmentHandler)**（:43-45）；**监听器 Bean**（:48-53——@ConditionalOnBean(EventPublishingRegistrationAspect)（:49——**依赖 05 仓库 AOP 注册事件切面存在**）+ @ConditionalOnMissingBean）；**监听器实现**（ZoneAttachmentListener :23-33——ApplicationListener\<RegistrationPreRegisteredEvent> + ApplicationContextAware——**注册前事件 → getBean(ZoneAttachmentHandler) → attachZone(registration.getMetadata())**（:29-32——**注册前把 zone 写入元数据**——配合 05 仓库注册事件体系）
- **对比取舍**：**知识增量**：①**跨仓库条件装配**（依赖 05 仓库的事件体系——AOP 切面 Bean 存在才装配监听器——**分层集成**）；②**@AutoConfigureAfter 值+名混合**（:37-41——自家类用 value、官方类用 name（避免编译依赖））；③**注册前事件时机**（PreRegistered——元数据在真正注册前完成附加——**事件时机选择**（与 05 仓库四态事件呼应））
- **测试佐证**：ZoneCloudAutoConfigurationIntegrationTest（:44-59——InMemoryServiceRegistry + 简单自动注册启用——**全链路装配实证**（Registration/ServiceRegistry/ZoneContext/CompositeZoneLocator/ZoneAttachmentHandler/ZoneAttachmentListener 全部注入 :62-78））；ZoneCloudAutoConfigurationTest（条件双路径：类存在 ZoneAttachmentHandler/Listener :55-56 + 缺失 ZoneLocator/Aspect/RegistrationPreRegisteredEvent/Registration :67-70）
- **my-xhs**：**该用没用（部分被替代）**——my-xhs **无 ZoneAttachmentHandler**（zone 包无 handler 实证）——但用 `spring.cloud.nacos.discovery.metadata.zone` **配置声明式**替代（ZoneContextAutoConfiguration:42——Nacos 注册时自带 metadata——**无需注册前附加钩子**）——注册前附加机制对 my-xhs 非必要

#### KP-515 `CloudServerZoneResolver` 实例元数据解析（CloudServerZoneResolver.java:16-31）

- **维度**：[分布式问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：ServiceInstance.getMetadata（Spring Cloud）、ZoneResolver 泛型（KP-502）
- **需求**：**从 ServiceInstance.metadata 解析 zone**——消费端读取注册元数据中的 zone（与 KP-504 生产端成对）
- **参考实现**：**单例**（:21——`public static final INSTANCE`——**无状态解析器单例**）；**metadata 读取**（:24-30——getMetadata() 非空 → get(ZONE_PROPERTY_NAME)——**同一属性名契约**（KP-504 写 / KP-515 读——生产者消费者闭环））
- **my-xhs**：**已用（实证）**——`my-xhs-common/.../zone/loadbalancer/ServiceInstanceZoneResolver.java`（ServiceInstance metadata 读 zone——与微球 CloudServerZoneResolver 同构）

#### KP-516 LoadBalancer 定制装配 + 优化版 ZonePreference Supplier（CustomizedLoadBalancerAutoConfiguration:14-21 + CustomizedLoadBalancerClientConfiguration:51-102 + ZonePreferenceServiceInstanceListSupplier:34-52）

- **维度**：[分布式问题]（负载均衡）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（装饰链 + 子上下文） | **置信度**：High
- **前置**：ServiceInstanceListSupplier.builder（官方装饰链）、DelegatingServiceInstanceListSupplier、@LoadBalancerClients(defaultConfiguration)、子上下文属性（LoadBalancerEnvironmentPropertyUtils）、Reactive/Blocking 双栈
- **需求**：**微球版 Zone 优先路由**——SCL 官方 Supplier 只按 zone 过滤，微球版集成 ZoneContext 精细策略（禁用区/就绪率/最小可用——KP-503）（历史 REQ-003 落地）——**默认不启用**（`microsphere.spring.cloud.loadbalancer.customized=true` 才装配）
- **参考实现**：**总开关**（CustomizedLoadBalancerAutoConfiguration :15——@ConditionalOnProperty("microsphere.spring.cloud.loadbalancer.customized", havingValue="true")——**默认关闭**（不干扰官方 LoadBalancer）+ @ConditionalOnClass(LoadBalancerClient) + **@LoadBalancerClients(defaultConfiguration=CustomizedLoadBalancerClientConfiguration)**（:19——**子上下文默认配置注册**）；**子上下文双栈配置**（CustomizedLoadBalancerClientConfiguration :54-86——ReactiveConfiguration（@ConditionalOnReactiveDiscoveryEnabled + @Order(193827465) :54-55）/ BlockingConfiguration（@ConditionalOnBlockingDiscoveryEnabled + @Order(193827466) :71-72）——**响应式/阻塞双栈各自的 Supplier 装配**；**装饰链构建**（:64-67/:81-84——`ServiceInstanceListSupplier.builder().withDiscoveryClient().withCaching().with((ctx, delegate) -> new ZonePreferenceServiceInstanceListSupplier(delegate, filter))`——**官方 builder 链 + 自定义装饰器**（Discovery → Caching → Zone 过滤））；**子上下文级条件**（OptimizedZoneConfigurationCondition :93-101——`LoadBalancerEnvironmentPropertyUtils.equalToForClientOrDefault("configurations", "optimized-zone-preference")`——**按客户端配置名匹配**（`spring.cloud.loadbalancer.configurations=optimized-zone-preference` 才生效））；**过滤器 Bean**（:88-91——ZonePreferenceFilter\<ServiceInstance> + CloudServerZoneResolver——KP-503 + KP-515 组装）；**Supplier 装饰器**（ZonePreferenceServiceInstanceListSupplier :34-52——extends **DelegatingServiceInstanceListSupplier**（官方装饰基类 :34）——get() → delegate.get().map(filteredByZone)（:45-47——**Flux.map 过滤**——响应式装饰器）
- **对比取舍**：**知识增量**：①**官方 Supplier 的同名重写**（:34——与官方 `org.springframework.cloud.loadbalancer.core.ZonePreferenceServiceInstanceListSupplier` **同名**（历史 REQ 缺陷表实证——**IDE import 易混淆**（微球包 io.microsphere... vs 官方 org.springframework...——**命名空间冲突的 API 风险**）——但包名不同可共存）；②**builder 装饰链**（官方 with() 扩展点——**自定义 Supplier 的标准接入方式**（与官方 ZonePreference 同构））；③**子上下文级条件匹配**（LoadBalancerEnvironmentPropertyUtils.equalToForClientOrDefault——**客户端级配置名条件**（per-client））；④**双栈装配**（Reactive/Blocking @Order 不同——**响应式/阻塞双栈并行**（呼应 05 仓库双栈模式））；⑤**默认关闭策略**（customized=true 才启用——**不影响官方行为的保守集成**）；⑥**缺陷（深度 review 实证）——Reactive 分支条件矛盾**：ReactiveConfiguration 类级 `@ConditionalOnReactiveDiscoveryEnabled`（:54——响应式发现可用）但 bean 级 `@ConditionalOnBean(DiscoveryClient.class)`（:59——**DiscoveryClient 是阻塞接口**）+ builder 用 `withDiscoveryClient()`（:64——阻塞构建）——**纯响应式应用（仅 ReactiveDiscoveryClient 无 DiscoveryClient）→ bean 条件不匹配 → 优化版 Supplier 不装配**——类级注解与实现语义矛盾（官方 Reactive 配置应依赖 ReactiveDiscoveryClient——本地无 SCL 源码对照，凭 API 语义判定——**置信度 Medium**）
- **my-xhs**：**已用（已深读 2026-08-13，High）**——`zone/loadbalancer/ZonePreferenceServiceInstanceListSupplier.java:19-38`（**逐行同构移植**：extends DelegatingServiceInstanceListSupplier :19 + get()→map(filterByZone) :31-36）+ `ZoneLoadBalancerConfiguration.java:26-76`；**与微球差异**：①**装配简化**——单级 @Configuration 直接注册（微球两级：AutoConfiguration 总开关 + @LoadBalancerClients 子上下文）——**my-xhs 不走子上下文**（丢失 per-client 隔离——主上下文单例 Supplier 全局共享）；②**开关合并**——`myxhs.availability.zone.preference.enabled=true` 直接启用（:28——微球独立 customized 开关 + 客户端级 optimized-zone-preference 条件——my-xhs 无 OptimizedZoneConfigurationCondition）；③**双内部类同名 Bean**（:45/:65 均 optimizedZonePreferenceServiceInstanceListSupplier + @ConditionalOnMissingBean(name=...) :44/:64 **显式互斥**——微球两个不同方法名——my-xhs 互斥更严谨）；④**缺陷 R1 在 my-xhs 同样存在**（:38 ReactiveConfiguration 类级 @ConditionalOnClass(ReactiveDiscoveryClient) 但 bean :43 @ConditionalOnBean(DiscoveryClient.class) 阻塞——纯响应式应用不装配——与微球同缺陷）；⑤**关联修复**：ServiceInstanceZoneResolver.java:22-24 有 **serviceInstance null 防御**——微球 CloudServerZoneResolver:24-30 无此防御（my-xhs 修复微球 null 隐患）

### 包总结（boot + cloud 批 3）

- **核心命题**：**"自动装配闭环 + 官方 LoadBalancer 的优化版替换"**——boot 层装配（ZoneContext 单例 Bean 化 + ZoneLocator 双通道收集）→ cloud 层注册元数据（PreRegistered 事件附加 zone）→ LoadBalancer 装饰链（默认关闭、客户端级条件、双栈）
- 生态呼应：05 仓库注册事件体系（RegistrationPreRegisteredEvent/EventPublishingRegistrationAspect）是本层装配前置条件——**跨仓库依赖链实证**
- 历史 REQ 落地：REQ-003（优化版 Supplier :34-52）✅ REQ-004（ZoneAttachmentListener :28-33）✅

### 包: `io.microsphere.multiple.active.zone.spring.aws`（批 4a：aws 3 文件）

#### KP-517 AWS 定位器家族——云元数据自动发现（Ec2AvailabilityZoneEndpointZoneLocator:22-66 + EcsContainerMetadataFileZoneLocator:31-75 + EcsTaskMetadataEndpointV4ZoneLocator:30-77）

- **维度**：[分布式问题]（区域发现）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（云元数据探测） | **置信度**：High
- **前置**：AWS EC2 Instance Metadata Service（IMDS 169.254.169.254 链路本地地址）、ECS 环境变量注入（ECS_CONTAINER_METADATA_FILE/URI_V4）、Jackson ObjectMapper
- **需求**：**从云元数据自动发现 Zone**——部署环境自动注入区域信息，运维不手写（历史 REQ-001"3 个内置实现：AWS EC2 metadata、AWS ECS task metadata V4、自定义"）
- **参考实现**：**三种探测源**：①**EC2 metadata endpoint**（Ec2AvailabilityZoneEndpointZoneLocator——supports 恒 true :36 + 默认 URI `http://169.254.169.254/latest/meta-data/placement/availability-zone`（:24——**IMDS 链路本地地址**（无需公网）+ 属性可覆盖 EC2_AVAILABILITY_ZONE_ENDPOINT_URI :22）+ HttpUtils.doGet（:45——KP-506 消费）+ timeout 从 Environment 注入（:63-65））；②**ECS metadata 文件**（EcsContainerMetadataFileZoneLocator——supports = **环境变量存在探测**（:45——`environment.containsProperty(ECS_CONTAINER_METADATA_FILE)`——**属性存在性即支持性**）+ FileInputStream 读 JSON（:53-58）+ `AvailabilityZone` 字段取值（:37/:59-60））；③**ECS task metadata V4**（EcsTaskMetadataEndpointV4ZoneLocator——supports = ECS_CONTAINER_METADATA_URI_V4 存在 :46 + **URI + "/task" 拼接**（:54）+ HttpUtils.doGet + JSON 解析 :56-60）；**order 链**（ECS File=5 :35 → ECS V4=10 :34 → EC2=15 :26 → Default=20（KP-509）——**探测优先级链**（容器内环境变量优先于 EC2 IMDS）
- **对比取舍**：**知识增量**：①**云元数据服务探测模式**（IMDS 链路本地地址 + 环境变量注入——**K8s/AWS 平台注入 vs 应用自发现**（K8s Downward API 同族））；②**supports 用 containsProperty**（:45/:46——**环境变量存在性 = 平台环境判定**（在 ECS 上才 support——探测链自动跳过非 ECS 环境））；③**JSON 解析 ObjectMapper 每次新建**（:57——new ObjectMapper() per call——可复用单例（性能细节）；④**catch Throwable 降级**（:63-64/:48-49——云 endpoint 不可达返回 null——Composite 继续下一探测源）；⑤**缺陷（深度 review 实证）——Ec2 supports 恒 true 导致非 AWS 环境启动延迟**：Ec2AvailabilityZoneEndpointZoneLocator.supports 恒 true（:35-36）且 order=15 排在 Default(20) 前——**非 AWS 环境（无 IMDS）每次定位都会先请求 169.254.169.254（默认 3 秒超时）失败后才到 Default**——启动 +3 秒延迟（imds 不可达时 doGet 阻塞至超时）——正确应像 ECS 定位器一样用环境变量/属性探测 supports（AWS EC2 专属标识）
- **测试佐证**：无 aws 测试（文件级穷尽自检：aws 模块 0 测试文件——集成风险点：IMDS 不可达路径无测试覆盖）
- **my-xhs**：**该用没用（真实差距）**——my-xhs 无云元数据探测族（zone 包无 aws 对应物实证——Nacos metadata 声明式替代）；国内云厂商 metadata 同族模式可借鉴（K8s Downward API/节点标签为现代替代）

### 包: `io.microsphere.multiple.active.zone.netflix`（批 4b：netflix 6 文件）

#### KP-518 Netflix 集成族——Eureka/Ribbon 适配（EurekaInstanceInfoZoneResolver:16-24 + ZoneAttachmentPreRegistrationHandler:18-36 + RibbonServerZoneResolver:29-35 + ZonePreferenceServerListFilter:33-45 + DiscoveryClientServer:31-86 + DiscoveryClientServerList:33-56）

- **维度**：[分布式问题]（服务治理集成）| **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[过时→替代]（Ribbon 2.7.18 已过时——Spring Cloud LoadBalancer 替代；Eureka 保留） | **置信度**：High
- **前置**：Ribbon Server/ServerList/ServerListFilter、Eureka InstanceInfo/PreRegistrationHandler/ApplicationInfoManager、ServiceInstance（Spring Cloud）、Netflix 生态过时背景
- **需求**：**microsphere Zone 体系接入 Netflix OSS**——Eureka 注册元数据附加（原生 PreRegistrationHandler 钩子）+ Ribbon ServerList 链的 Zone 过滤（Ribbon 时代的多活路由）
- **参考实现**：**Eureka 侧**：①**EurekaInstanceInfoZoneResolver**（:16-24——InstanceInfo.metadata 读 ZONE_PROPERTY_NAME——消费端）；②**ZoneAttachmentPreRegistrationHandler**（:18-34——implements **Eureka 官方 PreRegistrationHandler**（:18——Eureka 原生注册前钩子接口）+ beforeRegistration（:30-33——ApplicationInfoManager.getInfo() → metadata → attachZone——**REQ-004 的 Eureka 落地**（与 cloud 侧 ZoneAttachmentListener 双实现——**同一需求两套框架适配**））；**Ribbon 侧**：③**RibbonServerZoneResolver**（:29-34——`server.getZone()`——Ribbon Server 原生 zone 字段——**零转换**）；④**ZonePreferenceServerListFilter**（:33-44——implements Ribbon ServerListFilter + 委托 ZonePreferenceFilter（KP-503）——**泛型过滤适配**（Ribbon 过滤链接入微球过滤算法））；⑤**DiscoveryClientServer**（:31-56——**Ribbon Server 包装 ServiceInstance**（super(scheme, host, port) :38——桥接构造）+ **setZone(resolveZone)**（:41——构造时从 ServiceInstance 解析 zone 写入 Server.zone（CloudServerZoneResolver.INSTANCE :45——跨仓库单例复用））+ getId 覆盖 :49-51 + **ServiceInstanceMetaInfo 内部类**（:58-85——Ribbon MetaInfo 适配器——getAppName=getServiceId :67-68/getServerGroup=null :72-73/getInstanceId :82-83））；⑥**DiscoveryClientServerList**（:33-55——implements ServerList\<DiscoveryClientServer>——getInstances(serviceName) → stream map DiscoveryClientServer :51-54——**Ribbon 服务列表 = Spring Cloud 发现结果**（两套负载均衡生态桥接））
- **对比取舍**：**知识增量**：①**Ribbon ServerListFilter 链**（ZonePreferenceServerListFilter 是 Ribbon 官方同名类（com.netflix.loadbalancer.ZonePreferenceServerListFilter）的微球替代——**官方同名重写模式**（同 KP-516 SCL Supplier 同名——本仓库两处同名先例））；②**Server 包装 ServiceInstance**（:38/:41——**跨生态桥接类**（Ribbon Server 语义字段 + ServiceInstance 数据）——构造期一次性映射）；③**Eureka 原生钩子 vs AOP 事件**（PreRegistrationHandler（netflix 官方接口）vs ZoneAttachmentListener（cloud 层微球注册事件）——**同一需求、两套框架适配路径**（官方接口优先））；④**过时判定**：Ribbon 2.7.18（pom 实证）——**netflix 模块整体过时**（Spring Cloud LoadBalancer 已替代——main 分支 0.2.11 仍兼容的动机是旧版本生态）；⑤**缺陷**：getServerGroup 恒 null（:72-73——Ribbon 分组特性未实现）
- **测试佐证**：无 netflix 测试（0 测试文件——同 aws——**集成模块测试盲区**）
- **my-xhs**：**不该用**——Ribbon 已过时（官方 LoadBalancer 替代）；Eureka metadata 附加机制可借鉴（Nacos 注册前附加同族）

### 包总结（aws + netflix 批 4）

- **核心命题**：**"云元数据探测源 + 旧生态适配"**——aws 提供 3 探测源（环境变量存在性判定 + IMDS 端点 + 文件/HTTP JSON 解析），order 链 5/10/15 形成优先级；netflix 是 Ribbon/Eureka 时代的适配层（两处官方同名重写 + Server 包装 + 原生钩子）
- 过时标注：netflix 模块整体 [过时→替代]（Ribbon）；aws 模式时间无关（云元数据探测是通用模式）
- 测试盲区实证：aws/netflix 两模块 0 测试文件

### 包总结（spring 批 2）

- **核心命题**：**"Zone 自动发现链 + 配置变更双事件桥接"**——ZoneLocator SPI（supports/locate）→ Composite（排序+缓存+fast-fail+系统属性回写）→ Default（兜底）→ Listener（配置变更→ZoneContext→Spring 事件）
- 关键设计：类名探测多容器支持（无编译依赖）、JavaBeans→Spring 双事件桥接、系统属性做 Spring↔非 Spring 全局通道
- 与 KP-501 呼应：CompositeZoneLocator :98 写系统属性 ↔ ZoneContext.getCurrentZone :233 读——双通道设计自洽

### 包总结（commons 批 1）

- **核心命题**：**"非 Spring 全局可达的 Zone 状态 + 多级保护的区域路由"**——ZoneContext（状态+事件）→ ZonePreferenceFilter（消费）→ ZoneAttachmentHandler（生产元数据）→ ZoneResolver（解析抽象）→ ZoneConstants（属性契约）
- 与官方对照：SCL ZonePreferenceServiceInstanceListSupplier 只有"同区过滤"——microsphere 叠加禁用区/就绪率/最小可用三重保护 + 自动发现（REQ 表实证）
- 缺陷：ZoneContext 双通道状态分裂（字段 vs 系统属性）、FILER 拼写、HttpUtils info 级日志泄漏

---

## 三、深度 review 七项报告（批 1-4 合并）

> 2026-08-13 批判性 review：穷尽性核对先行——31/31 生产文件全覆盖（commons 6 + aws 3 + netflix 6 + spring 7 + boot 3 + cloud 6）。
> **深度 review 轮（方法论 §3 六项自查逐条执行）**：测试断言行号全量 grep 实证 + 3 处新缺陷发现 + 1 处引用目标修正（见下）。

- [x] **① 源码行号精确核对**：KP-501:34/:195-204/:233、KP-503:165（zoneCount*100/entitiesSize）、KP-505:108（FILER 拼写）、KP-508:98/:105、KP-510:72-76/:109-118/:139-165/:183-192、KP-512:47、KP-514:26-53、KP-516:34/:54/:59/:64/:93-101、KP-517:24/:35-36/:45/:54——全部 grep 实证 ✓
- [x] **② 穷尽性**：31/31 生产文件全覆盖（脚本核对 basename 逐文件 grep 文档）✓
- [x] **③ 空节标注**：aws/netflix 无测试文件——测试盲区已标注（KP-517/KP-518）✓
- [x] **④ 过时三级**：16 KP 全部标注（KP-506 过时→HttpClient、KP-518 netflix 整体过时→LoadBalancer）✓
- [x] **⑤ 重复内容**：ZoneAttachmentHandler 三处消费（ZoneAttachmentListener/KP-514、ZoneAttachmentPreRegistrationHandler/KP-518、attachZone 本体 KP-504）；ZonePreferenceFilter 两处集成（Ribbon Filter KP-518 + SCL Supplier KP-516）；同名类两处（KP-516 SCL Supplier / KP-518 Ribbon Filter）✓
- [x] **⑤b 引用目标核对（深度 review 修正）**：`DelegatingServiceInstanceListSupplier`/`LoadBalancerEnvironmentPropertyUtils`/`ServiceInstanceListSupplier.builder()`——**本地 spring-cloud-loadbalancer 无源码无 jar（find 实证全盘无）**——官方类行为断言（"官方仅按 zone 过滤"等）基于 API 语义判定——**KP-503/KP-516 官方对照断言置信度降为 Medium 并已标注**；JDK11 HttpClient 替代物实证存在（openjdk11u 源码）✓；EurekaClientConfigBean#DEFAULT_ZONE（spring-cloud-netflix 本地有）✓
- [x] **⑥ 诚实标注**：KP-505/506 深读完成；官方 SCL 断言降置信度已标注；aws/netflix 无测试已如实标注 ✓
- [x] **⑦ 命名空间迁移**：N/A ✓

### 深度 review 新发现（逻辑证伪轮，方法论 §3.2）

| # | 发现 | 源码实证 | 落点 | 置信度 |
|---|------|---------|------|--------|
| R1 | **Reactive 分支条件矛盾**：ReactiveConfiguration 类级 @ConditionalOnReactiveDiscoveryEnabled vs bean 级 @ConditionalOnBean(DiscoveryClient.class)（阻塞接口）+ withDiscoveryClient()——纯响应式应用（无 DiscoveryClient Bean）优化版 Supplier 不装配 | CustomizedLoadBalancerClientConfiguration:54/:59/:64 | KP-516 | Medium |
| R2 | **ORIGINAL_ZONE 运行期二次回退失效**：revertOriginalZone → zoneLocator.locate() → Composite 缓存命中（:51）——首次定位后改回 originalZone 不重新探测 | ZoneContextChangedListener:183-192 + CompositeZoneLocator:51 | KP-510 | High |
| R3 | **Ec2 supports 恒 true → 非 AWS 启动 +3s**：order=15 在 Default(20) 前 + IMDS 默认 3 秒超时——非 AWS 环境每次定位先等 IMDS 超时 | Ec2AvailabilityZoneEndpointZoneLocator:35-36/:24 + DefaultZoneLocator:17 | KP-517 | High |

### 测试扫描记录（02 §2.1，全部 14 个测试文件）

| 测试文件 | 验证了 | 结论 |
|---------|--------|------|
| ZoneContextTest | 单例（:45-47）、7 默认值（:51-58）、同值不触发事件（:71-77）、trim（:88-90）、逗号拆分（:136-138）——行号 grep 实证 | KP-501 ✓ |
| ZonePreferenceFilterTest | 15 场景全断言（null/空/单元素 :65-77、双开关 :84-99、大小写忽略 :116、就绪率 :149、最小可用 :164、禁用区 :208、null 实体 :228）——行号 grep 实证 | KP-503 ✓ |
| ZoneResolverTest | apply 委托 resolve（:14-18）、null 实体 | KP-502 ✓ |
| ZoneAttachmentHandlerTest | 有效/空白/默认/不可变 Map 四断言（:35-67） | KP-504 ✓ |
| HttpUtilsTest | null/空/blank（:22-36）、非 HTTP 协议（:40-48）、自建 HTTP Server 端到端（:52-69）——行号 grep 实证 | KP-506 ✓ |
| ZoneConstantsTest | 常量完整性全断言（:69-115） | KP-505 ✓ |
| CompositeZoneLocatorTest | null 构造（:112-113）、缓存（:142-150）、fast-fail null（:172-176）、异常降级（:188-194）——行号 grep 实证 | KP-508 ✓ |
| ZoneContextChangedListenerTest | supportsEventType 分支 + 属性同步（:83-127） | KP-510 ✓ |
| ZoneUtilsTest / DefaultZoneLocatorTest / ZoneContextChangedEventTest | 工具/兜底/事件载体 | KP-511/509 ✓ |
| ZoneAutoConfigurationTest | 条件双路径（存在/缺失类） | KP-512 ✓ |
| ZoneCloudAutoConfigurationTest / IntegrationTest | 条件双路径 + 全链路装配注入（:44-83） | KP-514 ✓ |

### 历史 REQ/分析交叉验证清单（06-multiactive 完整版）

> 来源：`microsphere-analysis/17-microsphere-multiactive-analysis/17-REQ-requirements-spec.md`（REQ-001~004）+ 9 篇分析
> 状态：✅ 已验证补入 KP / ❌ 证伪

| # | 历史断言 | 验证 | 落点 |
|---|---------|------|------|
| REQ-001 | ZoneLocator SPI（supports+locate，第一个 supports 生效） | ✅ 证实（ZoneLocator:20/:27 + CompositeZoneLocator:61-67 首个成功 break） | KP-507/508 |
| REQ-002 | ZoneContext 单例 7 volatile 属性 + PropertyChangeSupport 值变才触发 + getCurrentZone 读系统属性 | ✅ 全部证实（:34/:36-48/:50/:195-204/:233） | KP-501 |
| REQ-003 | 优化版 SCL Supplier（minAvailable/readyPercentage/disabledZone 精细策略） | ✅ 证实（ZonePreferenceServiceInstanceListSupplier:34-52 + ZonePreferenceFilter:36-185 + CustomizedLoadBalancerClientConfiguration:64-67 装饰链） | KP-516 |
| REQ-004 | ZoneAttachmentHandler 写 metadata["zone"] | ✅ 证实（:28-42） | KP-504 |
| 缺陷-同名类 | 微球 Supplier 与官方 SCL 同名——IDE import 混淆 | ✅ 证实（:34 extends Delegating + 包名 io.microsphere vs org.springframework——**同名不同包**） | KP-516 |
| 17-01-双路径幂等 | Eureka + Spring Cloud 并存时 attachZone 双调用——幂等 | ✅ 证实（netflix PreRegistrationHandler + cloud ZoneAttachmentListener 双路径；attachZone put 覆盖幂等） | KP-514/518 |
| 17-07-时序问题 | 定位失败（AWS 超时）→ 区域空 → 不写 metadata → 就绪率计算受影响 | ✅ 证实（attachZone :30 isNotBlank 守卫 + ZonePreferenceFilter :93 zoneCount 只计非空） | KP-504/503 |
| 17-07-Listener 断言 | **ZoneContextChangedListener 只实现 PropertyChangeListener 不实现 ApplicationListener** | ❌ **证伪**（源码实证 :56——implements **SmartApplicationListener**, ApplicationContextAware, EnvironmentAware——**历史断言错误**（SmartApplicationListener extends ApplicationListener——监听器同时是桥接者与 Spring 事件监听者）；但历史"自消费"动机分析合理（本类监听容器事件而非自身事件——无自消费） | KP-510 |

**验证成果**：REQ 4 项全部证实；历史缺陷 2 项证实；**1 项证伪**（17-07 Listener 断言——历史分析又一处结论需源码实证的案例）

---

## 四、my-xhs 落地判定汇总表（2026-08-13 实证修正版）

> **修正说明**：本会话深度 review 时发现多 KP 判定错误——my-xhs 已整体移植 zone 机制（`my-xhs-common/.../com/myxhs/common/zone/` 17 文件 + 4 测试）——"已用"判定全部经代码实证（非凭印象），"该用没用"为真实差距。

| KP | 判定 | 说明 |
|----|------|------|
| KP-501 | 已用 | ZoneContext 移植版（diff 实证逐字段对应）+ ZoneContextAutoConfiguration Bean 化 + ZoneProperties（@ConfigurationProperties 替代常量接口） |
| KP-502 | 已用 | ZoneResolver 移植 + ServiceInstanceZoneResolver |
| KP-503 | 已用 | ZonePreferenceFilter 移植（三重保护全保留 + 完整测试） |
| KP-504 | 该用没用（被替代） | 无 attachZone——Nacos metadata 配置声明替代（无需注册前钩子） |
| KP-505 | 已用 | ZoneConstants 移植 + ZoneProperties 现代化替代 |
| KP-506 | 不该用 | JDK11 HttpClient 替代；Spring RestTemplate/WebClient |
| KP-507 | 该用没用 | **真实差距**——my-xhs 无 supports/locate SPI（系统属性+Nacos fallback 单通道） |
| KP-508 | 该用没用 | **真实差距**——无组合定位器/fast-fail（无多源需求） |
| KP-509 | 已用（简化版） | ZoneContextAutoConfiguration:41-42 系统属性→Nacos metadata→defaultZone 兜底 |
| KP-510 | 已用（简化消费）+ 不该用（复杂桥接） | DynamicDataSource:88/:101 原生 PropertyChangeListener 切换数据源；无容器探测/Spring 事件桥接（场景不需要） |
| KP-511 | 不该用 | 工具类——getBean(Class) 更优 |
| KP-512 | 已用 | ZoneContextAutoConfiguration 单例 Bean 化 |
| KP-513 | 已用 | @ConditionalOnProperty matchIfMissing=true（单层条件） |
| KP-514 | 该用没用（被替代） | 无注册前附加——Nacos 声明式 |
| KP-515 | 已用 | ServiceInstanceZoneResolver（metadata 读 zone） |
| KP-516 | 已用 | ZonePreferenceServiceInstanceListSupplier 逐行同构移植（:19-38）+ ZoneLoadBalancerConfiguration（单级装配/开关合并/双内部类互斥——R1 条件矛盾同样存在；ServiceInstanceZoneResolver 补了 null 防御） |
| KP-517 | 该用没用 | **真实差距**——无云元数据探测（国内 Nacos 场景可接受） |
| KP-518 | 不该用 | Ribbon 过时；Eureka metadata 机制可借鉴 |

**汇总**：已用 10 / 该用没用 5（其中 2 项被 Nacos 声明式替代）/ 不该用 3。
**关键修正**：首版判定（已用 1/该用没用 13）**错误率 12/16**——教训：my-xhs 判定必须实证，禁止凭印象（方法论坑清单 #6 的判定版）。
**my-xhs 自主增量（微球没有的）**：`zone/redis/` 族 7 文件（RedisInterceptorAutoConfiguration/RedisCommandEvent/EventPublishingRedisCommandInterceptor/RedisMethodContext/RedisMethodInterceptor/InterceptingRedisConnectionInvocationHandler/RedisTemplateWrapper——**多活 Redis 命令拦截与事件化**）+ `zone/datasource/DynamicDataSource`（**zone 变更 → 数据源切换**——与 dynamic 仓库知识强关联）+ `zone/loadbalancer/`（ZoneLoadBalancerConfiguration）。

