# 交接文档 — microsphere-source 源码提取（Session 007）

> **本文件是 microsphere-source 提取的唯一权威进度文档。** 接手前完整阅读本文 + 方法论，再动任何文件。
> 最后更新：2026-08-13
> 范围：microsphere 生态源码提取（source/）——独立于 microsphere-extraction（课程提取，已完成 stage-1~4）

---

## 一、任务背景

**目标**：对 microsphere 生态源码做"穷尽提取 + 现代实现映射 + my-xhs 落地判定"，最终产出源码侧知识图谱，与课程 L1 合并成 L3 总教学大纲。

**核心视角（与历史分析的本质区别）**：
- 历史 `microsphere-analysis/`（2026-07~08 做过一轮）**无方法论、不作数**——只作"交叉验证清单"（见 §四）
- 本提取**严格按方法论**：02 SOP（穷尽）+ 08（三层次）+ 04（过时三级）+ 09（前置/掌握度）+ 自研 01-SOP（现代实现映射与自主落地）

**每个知识点五件套**：需求 / 自主实现 / 参考实现 / 对比取舍 / **my-xhs 落地判定**（已用/该用没用/不该用——用户明确要求每 KP 保留）

---

## 二、目录结构与产出

```
microsphere-source/
├── discussion/2026-08-12-方向规划.md   ← 方向定稿（5 问结论 + 四步流程 + 穷尽+三层叠加）
├── method/01-现代实现映射与自主落地.md  ← 方法论 SOP（试点沉淀 + 8 条坑清单）
├── mapping/                            ← L1 提取表（穷尽映射 + 三层叠加）——当前主产出
│   ├── 01-confucius-commons.md         35 KP ✅
│   ├── 02-microsphere-java.md          53 KP ✅
│   ├── 03-microsphere-spring.md        33 KP ✅
│   ├── 04-microsphere-spring-boot.md   17 KP ✅
│   ├── 05-microsphere-spring-cloud.md  15 KP ✅
│   ├── 06-microsphere-multiactive.md   16 KP ✅
│   ├── 07-microsphere-dynamic.md      20 KP ✅
│   ├── 08-microsphere-configuration.md  3 KP ✅
│   ├── 09-microsphere-gateway.md        5 KP ✅
│   ├── 10-microsphere-redis.md          6 KP ✅
│   ├── 11-microsphere-observability.md   4 KP ✅
│   ├── 12-microsphere-mybatis.md         6 KP ✅
│   └── 13-microsphere-alibaba-druid.md   3 KP ✅（本会话）
└── outline/                            ← 源码侧知识大纲（L1.5，每仓库必产）
    ├── 01-confucius-commons-jdk知识大纲.md       7 维度 35 KP ✅
    ├── 02-microsphere-java-生态设计模式知识大纲.md 9 维度 53 KP ✅
    ├── 03-microsphere-spring-扩展机制知识大纲.md   5 维度 33 KP ✅
    ├── 04-microsphere-spring-boot-扩展机制知识大纲.md 4 维度 12 节 17 KP ✅
    ├── 05-microsphere-spring-cloud-服务治理知识大纲.md 5 维度 11 节 15 KP ✅
    ├── 06-microsphere-multiactive-多活区域路由知识大纲.md 5 维度 16 KP ✅
    ├── 07-microsphere-dynamic-动态多数据源知识大纲.md 5 维度 20 KP ✅
    ├── 08-microsphere-configuration-配置中心注解化知识大纲.md 2 维度 3 KP ✅
    ├── 09-microsphere-gateway-动态端点路由知识大纲.md 5 维度 7 KP ✅
    ├── 10-microsphere-redis-基础设施增强知识大纲.md 4 维度 6 KP ✅
    ├── 11-microsphere-observability-可观测性增强知识大纲.md 3 维度 4 KP ✅
    ├── 12-microsphere-mybatis-executor增强知识大纲.md 4 维度 6 KP ✅
    ├── 13-microsphere-alibaba-druid-连接池增强知识大纲.md 3 维度 3 KP ✅
    └── 14-microsphere-sentinel-流控集成知识大纲.md 2 维度 3 KP ✅（本会话）
progress/HANDOVER-session007.md         ← 本文（唯一权威进度）
```

---

## 三、已完成状态（14/36 仓库，230 KP + 14 outline——含 02 补提取 3 KP + 08-14 问题域补 11 KP）

| # | 仓库 | 生产文件 | KP | 关键知识点 | outline |
|---|------|:---:|:---:|-----------|:---:|
| 01 | confucius-commons | 38 | 35 | JDK 内部机制触发面（findLoadedClass/Unsafe/attach/SPI）、移植来源三实证（Josh Bloch/JCIP/Android） | ✅ |
| 02 | microsphere-java | 323 | 53 | SPI 注册中心三处实证、MethodHandle 版本探测、配置元数据三阶段闭环、泛型模型、转换四方向族 | ✅ |
| 03 | microsphere-spring | 288 | 33 | 注入点解析、回调式责任链、BeanFactory 三时点、@Import 模板/可选导入、TTL cacheResolver | ✅ |
| 04 | microsphere-spring-boot | 73 | 17 | 前缀条件注解、Binder 绑定监听、条件评估报告、Boot3 兼容层、监控线程池 | ✅ |
| 05 | microsphere-spring-cloud | 78 | 15 | Union 多注册（短路 vs 全量）、注册四态事件、Feign 配置热更新、平滑加权轮询 | ✅ |
| 06 | **microsphere-multiactive** | **31** | **16** | **Zone 自动发现 SPI（supports/locate）、ZoneContext 全局单例+双事件桥接、三重保护区域路由、云元数据探测、注册元数据闭环、Ribbon/Eureka 适配** | ✅ |
| 07 | **microsphere-dynamic** | **81** | **20** | **子上下文隔离架构（每单元独立 AnnotationConfigApplicationContext）、4 SPI×6 模块管道、事件驱动重建（ZoneContextChangedEvent 消费方实证）、动态数据源两种架构对照（重建 vs 预建池）、Import 三件套、ORM 模块对称** | ✅ |
| 08 | **microsphere-configuration** | **11** | **5** | **配置中心注解化三件套（注解+Attributes+Loader）、Loader vs Registrar 双模式、Apollo 克隆替换热更新、自研 Nacos OpenAPI 客户端、三层层级抽象、历史 4 缺陷证实（D1-D4）** | ✅ |
| 09 | **microsphere-gateway** | **28** | **7** | **we:// 端点粒度动态路由、Filter 链缓存+事件重建、心跳禁用（G1 断链根源）、双栈、metadata 协议链路（G11/G12 根源）、演进史重构损失、G1-G15 缺陷表 15/15 全部证实** | ✅ |
| 10 | **microsphere-redis** | **86** | **7** | **命令拦截三回调+事件化（my-xhs zone/redis/ 族上游实证）、Kafka 逻辑复制、定长序列化家族、命令元数据+Doclet 生成、连接工厂 AOP 代理、问题域三主题（ThreadLocal 代价/双层代理/复制三陷阱 + V0/V1 版本化）** | ✅ |
| 11 | **microsphere-observability** | **39** | **5** | **InMemoryAppender 启动缓冲 + P1 时序 bug（时序铁律）、SkipList 去重副作用、Filter 桥接、Micrometer 补全、问题域（P2 @ConditionalOnBean 语义陷阱 + 静默失效家族）** | ✅ |
| 12 | **microsphere-mybatis** | **54** | **6** | **ExecutorFilter 过滤链（10/15 方法覆盖）、Interceptor 桥接、双 Enable 注解、测试解析器族（29 文件过半）、MP 生态对照、问题域（P1-P6 已证清单——P6 二级缓存短路/拼写第 7 例 Defintion）** | ✅ |
| 13 | **microsphere-alibaba-druid** | **19** | **4** | **官方 Filter 491 方法三层收敛、三源装配、P2 switch fallthrough、问题域（P1 双开关冗余/P3 空壳/P4 占位符 + buildResourceName SQL AST + P2 根源跨仓库对照）** | ✅ |
| 14 | **microsphere-sentinel** | **30** | **4** | **模板 API + 插件 SPI（五元组）+ JMX 插件仓库、6 适配器统一模式（5 类独有）、四重条件装配、问题域（三种上下文传递模式——ThreadLocal 异步丢失+08-redis 同族/高基数资源命名——裸 SQL vs PreparedStatement 过滤）** | ✅ |
| 07 | **microsphere-dynamic** | **81** | **20** | **子上下文隔离架构（每单元独立 AnnotationConfigApplicationContext）、4 SPI×6 模块管道、事件驱动重建（ZoneContextChangedEvent 消费方实证）、动态数据源两种架构对照（重建 vs 预建池）、Import 三件套、ORM 模块对称** | ✅ |
| 08 | **microsphere-configuration** | **11** | **5** | **配置中心注解化三件套（注解+Attributes+Loader）、Loader vs Registrar 双模式、Apollo 克隆替换热更新、自研 Nacos OpenAPI 客户端、三层层级抽象、历史 4 缺陷证实（D1-D4）** | ✅ |
| 09 | **microsphere-gateway** | **28** | **7** | **we:// 端点粒度动态路由、Filter 链缓存+事件重建、心跳禁用（G1 断链根源）、双栈、metadata 协议链路（G11/G12 根源）、演进史重构损失、G1-G15 缺陷表 15/15 全部证实** | ✅ |
| 10 | **microsphere-redis** | **86** | **7** | **命令拦截三回调+事件化（my-xhs zone/redis/ 族上游实证）、Kafka 逻辑复制、定长序列化家族、命令元数据+Doclet 生成、连接工厂 AOP 代理、问题域三主题（ThreadLocal 代价/双层代理/复制三陷阱 + V0/V1 版本化）** | ✅ |
| 11 | **microsphere-observability** | **39** | **5** | **InMemoryAppender 启动缓冲 + P1 时序 bug（时序铁律）、SkipList 去重副作用、Filter 桥接、Micrometer 补全、问题域（P2 @ConditionalOnBean 语义陷阱 + 静默失效家族）** | ✅ |
| 12 | **microsphere-mybatis** | **54** | **6** | **ExecutorFilter 过滤链（10/15 方法覆盖）、Interceptor 桥接、双 Enable 注解、测试解析器族（29 文件过半）、MP 生态对照、问题域（P1-P6 已证清单——P6 二级缓存短路/拼写第 7 例 Defintion）** | ✅ |
| 13 | **microsphere-alibaba-druid** | **19** | **4** | **官方 Filter 491 方法三层收敛、三源装配、P2 switch fallthrough、问题域（P1 双开关冗余/P3 空壳/P4 占位符 + buildResourceName SQL AST + P2 根源跨仓库对照）** | ✅ |
| 14 | **microsphere-sentinel** | **30** | **4** | **模板 API + 插件 SPI（五元组）+ JMX 插件仓库、6 适配器统一模式（5 类独有）、四重条件装配、问题域（三种上下文传递模式——ThreadLocal 异步丢失+08-redis 同族/高基数资源命名——裸 SQL vs PreparedStatement 过滤）** | ✅ |

**依赖链进度**：confucius-commons → microsphere-java → microsphere-spring → spring-boot → spring-cloud → multiactive → dynamic ✅（00 SOP §3.2 主线 + 应用层 2 仓走完）

**剩余仓库决策（用户 2026-08-13 修正）**：**sentinel（30 + alibaba-sentinel 30）恢复提取**（07-sentinel 历史材料丰富 + 5 类独有适配器——已完成：14 仓 3 KP）；**其余移出**——resilience4j（57）/dubbo（10）/tomcat/security（10）/netflix（8）为官方生态封装（另一 AI 梳理）；etcd（8）/hibernate（15）/logging（33）/i18n（36）为已提取模式同族重复；bom/build/microsphere-test 非知识仓——**提取阶段结束（14/36 仓库，229 KP + 14 outline）**——进入收尾阶段（L2 聚合/L3 大纲/my-xhs 差距清单）
> **决策（用户 2026-08-13）**：**microsphere-nacos（128 文件）移出本提取队列**——手写 Nacos OpenAPI 客户端（openapi 118 + discovery-spring-cloud 10）——**由另一 AI 系统性梳理 Nacos 源码**——本队列不提取（避免重复劳动）；其知识本体（HTTP 客户端骨架/鉴权/序列化）已在 02/08 覆盖——若后续需要对照可在 L2 聚合时引用历史 06-REQ（含 HttpMethod.DELETE 写 "GET" :58 缺陷线索）

---

## 四、核心方法论沉淀（接手必读）

### 4.1 铁律（每批/每仓库执行，违反即重蹈覆辙）

1. **穷尽性核对先行**——每批写完后**立即**用 `find 文件清单 vs 文档提及` 核对（不等 review）——曾两次犯"归组过粗"（spring 42/47 未覆盖、spring-cloud 44/46 未覆盖）
2. **行号写入时验证**——`grep -n` 抄录，禁止凭文件总行数估算（批 2 曾整批行号错）
3. **测试同步扫描**（02 §2.1）——测试断言写入"测试扫描记录"表
4. **outline 必产**——每仓库完成后**必须**产 outline（曾漏 boot/cloud 两个，已补）
5. **outline 粒度**——每 KP 独立条目（或 2-3 同主题合并且各自列出），覆盖表声称 N/N 必须正文有对应条目（曾两次"覆盖表 53/53 但正文缺 25 条"）
6. **my-xhs 判定实证先行（session007 新增铁律）**——"已用"判定必须先查 my-xhs 代码（MCP data-workspace-my-xhs 索引 + 文件清单 + diff 对照）再写，禁止凭印象（曾 16 KP 判定错 12 个——my-xhs 已整体移植 zone 机制却标"该用没用"）
7. **穷尽性核对用 basename 级 grep（session007 新增铁律）**——"清单总数 = 已提取数"必须逐文件 basename grep 文档实证——**session005/006 交接声称 02/03"穷尽"被证伪**：02 342 文件 160 未提及（46.8%）、03 323 文件 168 未提及（52%）——多为归组未列全文件名（铁律 #1"覆盖表声称 N/N 但正文缺"规模化再犯），02 有 7 族**真遗漏**（event 10/io 序列化+文件监听/URLClassPathHandle 10/ClassFilter 6/ExecutorUtils/FastByteArray——已补 KP-120a/b/c）——**每仓库提取完成必须跑 basename 核对脚本**（本次补核对：01 缺 3 常量类已补列、04 缺 1、05 全齐）

### 4.2 历史 REQ 交叉验证（重大价值——本会话最大发现）

**历史分析缺陷表是"待验证假设库"——不盲信、逐个源码验证**。流程：

```
每仓库提取完成后 → 对照 microsphere-analysis/{NN}-REQ-requirements-spec.md 缺陷表（D 系列）
  → 逐个源码验证（grep 行号）
  → 证实的补入对应 KP（标注"历史 REQ Dxx 交叉验证"）
  → 证伪的标注（如 D11——历史说 ErrorDecoder.Default 抽象，Feign 源码实证是具体类）
  → 验证清单附 mapping 尾部（cloud 16 项/boot 6 项全部验证完——15 证实 + 1 证伪）
```

**跨会话验证成果累计**：
- session006：05-cloud + 04-boot 共 22 项历史缺陷——**21 证实 + 1 证伪**（D11）
- **session007：06-multiactive——REQ-001~004 全部证实 + 9 篇分析中 2 项缺陷证实 + 1 项证伪**（17-07 声称 "ZoneContextChangedListener 只实现 PropertyChangeListener 不实现 ApplicationListener"——**源码实证 :56 implements SmartApplicationListener**（extends ApplicationListener）——历史又一处结论需源码实证的案例；其"自消费"动机分析合理但实现路径断言错误）

### 4.3 深度 review 新增发现（session007 深度轮——方法论 §3 六项自查）

**测试断言行号全量 grep 实证** + **3 处新缺陷**（非历史来源，本会话源码逻辑证伪发现）+ **1 处引用目标修正**：

| # | 发现 | 实证 | 置信度 |
|---|------|------|--------|
| R1 | Reactive 分支条件矛盾（类级响应式 @ConditionalOnReactiveDiscoveryEnabled vs bean 级 @ConditionalOnBean(DiscoveryClient.class) 阻塞接口）——纯响应式应用优化版 Supplier 不装配 | CustomizedLoadBalancerClientConfiguration:54/:59/:64 | Medium |
| R2 | ORIGINAL_ZONE 运行期二次回退失效（revertOriginalZone → Composite 缓存命中不重新探测） | ZoneContextChangedListener:183-192 + CompositeZoneLocator:51 | High |
| R3 | Ec2 supports 恒 true → 非 AWS 环境启动 +3s（IMDS 超时） | Ec2AvailabilityZoneEndpointZoneLocator:35-36 | High |
| X1 | **⑤b 引用目标修正**：本地无 spring-cloud-loadbalancer 源码/jar（find 全盘实证）——官方类断言（"官方仅按 zone 过滤"）降置信度 Medium + 标注 | — | — |
| R4 | dynamic **启动忙等轮询无超时**（awaitTermination 循环——死锁永久挂起） | DynamicJdbcContextApplicationListener:140-150 | Medium |
| R5 | dynamic **config 原地清空副作用**（dynamic 分支清空 datasource/ha/sharding 字段——重建时从环境重读有补救） | DynamicJdbcContextProcessor:112-119 | Medium |
| R6 | dynamic **zone 属性名字符串硬编码**（"zone".equals——未用常量——改名静默失效） | PropagatingDynamicJdbcConfigChangedEventListener:91 | High |
| X2 | **两种动态数据源架构实证**：my-xhs DynamicDataSource（403 行）预建池切换（Map<zone,DataSource> + 连接计数等待 30s）vs microsphere 子上下文重建（delegate 交换 + 延迟关闭）——同名不同架构 | my-xhs zone/datasource/DynamicDataSource.java | High |

### 4.4 my-xhs 判定（2026-08-13 实证修正——本会话最大教训）

**判定三分法必须实证，禁止凭印象**——本会话首版 16 KP 判定（已用 1/该用没用 13）**错误率 12/16**：my-xhs 实际已整体移植 zone 机制（`my-xhs-common/.../com/myxhs/common/zone/` 17 文件 + 4 测试）——"已用"判定全部经代码实证（MCP 搜索 + 文件清单 + diff 对照）后才确认。

**my-xhs zone 包实证结论**：
- **移植（已用 10 KP）**：ZoneContext/ZoneResolver/ZonePreferenceFilter/ZoneConstants（diff 实证逐字段对应 + 现代化改写 @Slf4j/@ConfigurationProperties/私有构造）+ 单例 Bean 化（ZoneContextAutoConfiguration:26-46）+ 条件注解 + ServiceInstanceZoneResolver + ZonePreferenceServiceInstanceListSupplier（同名移植）+ DynamicDataSource 原生 PropertyChangeListener 消费（:88/:101）
- **差距（该用没用 5 KP）**：supports/locate 多源 SPI、组合定位器 fast-fail、云元数据探测族——my-xhs 用系统属性→Nacos metadata→defaultZone 单通道替代（ZoneContextAutoConfiguration:41-42）；注册前附加被 Nacos 配置声明替代
- **自主增量（微球没有）**：`zone/redis/` 族 7 文件（多活 Redis 命令拦截与事件化）+ `zone/datasource/DynamicDataSource`（zone 变更→数据源切换——**与 dynamic 仓库强关联**）+ zone/loadbalancer/

**教训（写入铁律）**：my-xhs 判定 = 提取层的实证动作（MCP 搜索 data-workspace-my-xhs + 文件清单 + diff），不是写作时的猜测——**每 KP 判定前先查 my-xhs 索引**。

---

## 五、关键知识索引（跨仓库引用，写总结勿写错）

### 生态递进链（多会话实证）
- **JDK SPI**（microsphere-java ServiceLoaderUtils）→ **SpringFactoriesLoader**（microsphere-spring）→ **@ConditionalOnXxx**（boot）→ **DiscoveryClient/ServiceRegistry**（cloud）→ **ZoneLocator 双通道装配**（multiactive ZoneAutoConfiguration :47 loadFactories + :50 Bean 收集）
- **注册事件体系**：cloud KP-405（RegistrationPreRegisteredEvent/EventPublishingRegistrationAspect）→ **multiactive KP-514 跨仓库消费**（@ConditionalOnBean(EventPublishingRegistrationAspect) + @AutoConfigureAfter ServiceRegistryAutoConfiguration）——**跨仓库条件装配实证**
- **双事件桥接**：ZoneContext（JavaBeans PropertyChangeSupport）→ ZoneContextChangedListener（临时监听器收集 → Spring 事件）——KP-510
- **系统属性全局通道**：CompositeZoneLocator :98 写 ↔ ZoneContext.getCurrentZone :233 读——KP-501/KP-508 双通道自洽

### 本仓库三处"官方同名重写"（命名冲突先例）
| 类 | 官方同名 | 风险 |
|----|---------|------|
| ZonePreferenceServiceInstanceListSupplier（cloud loadbalancer） | org.springframework.cloud.loadbalancer.core.* | IDE import 混淆 |
| ZonePreferenceServerListFilter（netflix ribbon） | com.netflix.loadbalancer.* | IDE import 混淆 |
| 历史 REQ 缺陷表已记录此风险 |

### 拼写错误/缺陷实证（API 稳定约束）
- `PREFERENCE_FILER_PROPERTY_NAME_PREFIX`（multiactive ZoneConstants :108）——FILER 应为 FILTER——同 StacKTrace/Pattens 先例
- ZoneContext 双通道状态分裂（setZone 写字段 vs getCurrentZone 读系统属性——设计自洽但 API 表面分裂）
- HttpUtils.doGet info 级日志打印完整响应（:44——生产泄漏隐患）
- ZonePreferenceFilter 返回引用语义不统一（disabled 分支新列表 vs 其余原引用）
- netflix 模块（Ribbon 2.7.18）+ HttpUtils（HttpURLConnection）——两处过时→替代已标注

### 测试盲区实证（诚实标注）
- **aws/netflix 两模块 0 测试文件**（IMDS 不可达路径/集成路径无测试覆盖）——集成模块风险点

---

## 六、待验证/待核对（接手注意）

### [待验证]（已清零——cloud/boot/multiactive 全部验证完毕）
- cloud D01-D16 + boot D01-D06：全部验证完（21 证实含 2 部分 + 1 证伪）
- multiactive REQ-001~004 + 9 篇分析 3 项：全部验证完（REQ 4 证实 + 缺陷 2 证实 + 1 证伪）——无遗留

### [待验证]（mapping 内部标注，后续仓库提取时顺手确认）
- 02 KP-117 `of10MethodHandle`（10 参 handle 存在但无对应公开重载）
- 03 KP-304/406 等 Medium 置信度项（未深读核心逻辑）
- 06 KP-503 就绪率整数除法边界（zoneCount*100/entitiesSize 截断行为——测试未覆盖低实例数截断边界）

### 剩余仓库的历史交叉验证材料（提取后必做）
| 仓库 | 历史缺陷表 |
|------|-----------|
| 18-dynamic | REQ 8 项 + 14 篇分析（与 multiactive ZoneContextChangedEvent 消费方强关联——**ZoneContextChangedListener 发布的事件由 dynamic 侧消费**——历史 17-07 提到） |
| 16-gateway | ✅ 已清（本会话 **15/15 全部证实**——G1-G15 清零） |
| 17-multiactive | ✅ 已清（本会话） |
| 09-observability | ✅ 已清（P1 证实——:85 Prepared vs :39 Started 对照——自动配置 Bean 早期事件不触发 + 第二丢失路径 SkipList 去重） |
| 14-druid | ✅ 已清（P2 switch fallthrough 证实——:69-78 三 case 无 break + 测试未断言次数） |

---

## 七、未决问题（用户决策）

1. **剩余 29 仓库顺序**：本会话完成 multiactive（应用层首个）——建议继续 dynamic（81 文件 + 与 multiactive 事件强关联 + 历史 14 篇分析）→ configuration（11 小仓试点快）→ gateway → nacos → redis——**顺序待用户确认**
2. **L2 聚合**（全部仓库后——跨仓库去重 + 5 维度聚合）
3. **L3 总教学大纲**（最终交付——课程 + 源码合并）
4. **my-xhs 差距清单执行**（"该用没用"项汇总——接 my-xhs-优化规划）
5. **L2 聚合（源码侧）✅ 进行中（本会话）**——`l2-aggregation/00-L2聚合-跨仓库知识图谱.md`——10 大复现模式家族（SPI 11/14/事件 11/14/条件注解 9/14/模板 9/14 等）+ 5 维度图谱 + 缺陷家族（7 拼写/3 静默/4 API 语义/3 同族）——**模式先于仓库**（学 1 模式 = 懂 11 仓）

---

## 八、git 约定

- 仓库：`/data/workspace/source-code/book/成长之路`，分支 `fresh`
- 远端：`git@github.com:LoveEleve/learn-book.git`
- **只提交 microsphere-source 相关文件**；不碰 source-analysis/issue、talk-method、issue 等他人项目未提交改动
- 本会话 commit 范围：`ae62866` 之后 ~（交接文档收尾 commit）——**multiactive 提取 4 批 + outline + 交接文档**

---

## 九、接手须知（下个 AI 第一件事）

1. **必读**：本文 + `method/01-现代实现映射与自主落地.md`（方法论）+ `discussion/2026-08-12-方向规划.md`（方向）
2. **流程**（每仓库）：建 MCP 索引（index_repository）→ 读上下文（README/pom/包结构）→ 分批提取（≤10 文件/批，穷尽性核对先行）→ 测试扫描 → 历史 REQ 缺陷交叉验证 → 七项 review 报告 → outline → 提交推送
3. **参考**：`mapping/01-06` 的格式与粒度（KP 编号：01 仓库 KP-01~35 / 02 仓库 KP-101~120（含子编号） / 03 仓库 KP-201~228 / 04 仓库 KP-301~317 / 05 仓库 KP-401~415 / 06~10 仓库 KP-501~906 / 11 仓库 KP-1001~1004 / 12 仓库 KP-1101~1106 / 13 仓库 KP-1201~1203 / 14 仓库 KP-1301~1303——**收尾阶段（L2/L3/差距清单）**）
4. **工具**：MCP 索引已建（confucius/java/spring/boot/cloud + multiactive + 历史仓库等）
5. **源码位置**：confucius 在 `source-code/code/microsphere/`；java/spring/boot 在 `cloud-native-code/share/`；spring-cloud/multiactive/configuration/dynamic/gateway/redis 在 `cloud-native-code/stage-4/`（注意：实际完整路径前缀是 `/data/workspace/java-training-camp/cloud-native-code/`）
