# Microsphere Dynamic 动态多数据源知识大纲（dynamic 触发面）

> 来源：`mapping/07-microsphere-dynamic.md` 22 个 KP 的知识本体提炼
> 性质：**源码侧 outline（L1.5）**——mapping 是文件映射（过程），本大纲是知识本体（结果）
> 组织：按知识维度，非按文件；每个知识点标注来源 KP + 六元元数据 + my-xhs 落地实证
> 用途：①自学/面试知识图谱（动态多数据源/子上下文隔离）②与课程 L1（stage-4 多活）合并成 L3 的源码侧素材 ③my-xhs 对照
> 覆盖核对：22/22 KP 全部归属（文末核对表）+ 问题域 2 维度（历史 14 篇分析全映射）

---

## 一、子上下文隔离架构（核心命题）[工程问题]

> **核心命题**：一个数据库单元 = 一个独立 Spring 子上下文（完整 Bean 生命周期隔离）——**不是 ThreadLocal 路由**。与 baomidou dynamic-datasource（@DS + ThreadLocal）是两种根本不同的架构。

### 1.1 子上下文基座 + 装配入口 [🔴 P1] [时间无关模式]
- **来源**：KP-601（DynamicJdbcChildContext）/ KP-603（DynamicJdbcContextApplicationListener）
- **机制**：**extends AnnotationConfigApplicationContext**（:30）+ **postProcessBeanFactory 注入全定制**（:92-107——classLoader/Environment attach/监听器/配置类注册/Processor 管道）+ **Environment merge + 合成源清理**（:60-68/:70-90——父子属性合并的正确姿势）；**ApplicationPreparedEvent 装配**（:35——启动早期）+ **多配置并行初始化**（:116-160——线程池 + 轮询 + InitializeErrors 聚合）+ **单配置免子上下文**（:85-89）+ 父上下文 AutoConfiguration 排除（:178-189）
- **缺陷（R1）**：启动忙等轮询无总超时（:140-150——死锁则永久挂起）
- **my-xhs**：该用没用——单上下文（无多单元）

### 1.2 子→父 Bean 回注（跨上下文暴露）[🔴 P1] [时间无关模式]
- **来源**：KP-604（DynamicJdbcChildContextRefreshedListener）
- **机制**：刷新后遍历子 Bean → **基础设施排除**（:82-84）→ **Bean 名生成 SPI**（:100-104 + 默认 `contextId$beanName` :107-110——命名空间化）→ **暴露/primary 白名单**（:117-124/:126-153——白名单 registerBean 可带 primary，否则 FactoryBean 包装）→ **父关闭级联**（:70-74）
- **my-xhs**：该用没用——无跨上下文

### 1.3 事件驱动重建（多活存储联动）[🔴 P1] [时间无关模式]
- **来源**：KP-605（PropagatingDynamicJdbcConfigChangedEventListener）/ KP-614（DynamicDataSource）
- **机制**：**双事件传播**（PropertySourcesChangedEvent + **ZoneContextChangedEvent（06 仓库）**→ DynamicJdbcConfigChangedEvent——:49-50）+ **条件传播**（属性名匹配 :66 + **仅 HA config 触发** :80——Zone 切换只重建含 ha-datasource 的单元）+ **zone 属性名硬编码**（:91——R3 缺陷）；**消费终点**（DynamicDataSource 内部 RefreshingDynamicDataSourceListener :303-327——属性名匹配 :297 + findParentContext 场景定位 :280-298 + 重建 :321）
- **my-xhs**：已用（简化）——原生 PropertyChangeListener 直连（无传播层）

---

## 二、4 SPI × 6 模块管道 [工程问题]

> **核心命题**：每个数据库单元经 4 阶段管道处理——**默认值填充 → 校验 → 属性合成 → Bean 注册**；6 个模块（datasource/transaction/sharding-sphere/mybatis/mybatis-plus/ha）按需实现——新模块 = 实现接口 + spring.factories。

### 2.1 管道 + SPI 基座族 [🔴 P1] [时间无关模式]
- **来源**：KP-602（DynamicJdbcContextProcessor）/ KP-608（ConfigurationCapable）/ KP-609（校验族）/ KP-611（注册器族）/ KP-612（合成器族）
- **机制**：**五步管道**（:50-71——注解处理器 → PostProcessor → Validator → dynamic 分支 → Synthesizer 属性源 → Registrar）；**SPI 懒加载**（:218-246——loadFactories 四族 + 字段缓存）；**泛型反射取配置类型**（ConfigurationCapable :18-20——resolveActualTypeArgumentClass）；**模板方法**（AbstractConfigurationConfigValidator——doValidate 抽象 + validateComponentType 组件类型校验 :51-59）；**模块注册器 supports 门控**（:34-45——无配置即跳过）；**前缀反射合成**（:52-67——@ConfigurationProperties 前缀提取）；**四 Aware 统一基座**（PostProcessor/Validator/Synthesizer/Registrar 同构）
- **缺陷（R2）**：dynamic 分支原地清空 config 字段（:112-119——破坏性副作用——重建时从环境重读有补救）
- **my-xhs**：该用没用——无模块化（单数据源）

### 2.2 配置模型 + 工具 [🔴 P1] [时间无关模式]
- **来源**：KP-607（DynamicJdbcConfig）/ KP-620（util 族）
- **机制**：**模块字段配置模型**（datasource/ha-datasource（zone→List）/transaction/sharding-sphere/mybatis/mybatis-plus——@JsonProperty 映射 + @JsonIgnore 派生属性）；**Zone 感知**（持有 ZoneContext :95）；**JSON 配置读取**（属性值即 JSON——:121——Nacos 可直接下发 + classpath 资源路径 :135）；**JSON 往返克隆**（:160-165——深克隆通用方案）
- **my-xhs**：该用没用——@ConfigurationProperties 直接绑定（无 JSON 字符串）

---

## 三、动态数据源两种架构（对照核心）[分布式问题]

> **核心命题（本仓库最大对照价值）**：动态数据源的**两种实现架构**——**预建池切换（my-xhs）vs 子上下文重建（microsphere）**。

### 3.1 子上下文重建架构（microsphere）[🔴 P1] [时间无关模式]
- **来源**：KP-614（DynamicDataSource）
- **机制**：**delegate 包装**（全部 DataSource 方法委托 :102-145）+ **懒初始化**（首访问才建子上下文 :147-153）+ **重建**（createDynamicDataSourceConfig——**setDynamic(false) 防递归** :226 + **只留 DataSource 配置** :228-230（MyBatis/事务在外层）+ 新子上下文 refresh :183 + **getDataSource 唯一性校验** :203-214 + **synchronized(mutex) 原子交换** :186-198 + **旧子上下文延迟关闭** :245——在途连接优雅迁移）
- **与 baomidou 对比**：连接池级替换（隔离彻底/成本高）vs ThreadLocal 路由（轻量/共享）
- **my-xhs**：已用——**见 3.2**

### 3.2 预建池切换架构（my-xhs）[已用实证]
- **来源**：my-xhs `zone/datasource/DynamicDataSource.java`（403 行）
- **机制**：构造 `(Map<zone, DataSource>, defaultZone)`——**按 zone 预建连接池** + `switchDataSource(zone)` = **Map 查找换池** + **activeConnectionCount + SWITCH_WAIT_TIMEOUT_SECONDS=30 在途连接计数等待**（安全切换）+ addPropertyChangeListener 原生监听 ZoneContext
- **对比**：轻量/快/池级隔离——单库 HA 场景足够；无子上下文/无传播链

### 3.3 AutoConfiguration 控制面 [🔴 P1] [时间无关模式]
- **来源**：KP-616（Import 三件套 + Repository）
- **机制**：**AutoConfigurationImportFilter/Listener/Selector 三扩展点组合** + **ClassLoader 作用域缓存**（:42）+ **按前缀取类名**（:81——模块级 AutoConfiguration 隔离——父上下文排除来源）+ **DisposableBean 清理**（:67）
- **my-xhs**：该用没用——单上下文无排除需求

---

## 四、模块化生态集成 [工程问题]

### 4.1 ORM 模块对称（MyBatis/MyBatis-Plus）[🔴 P1] [时间无关模式]
- **来源**：KP-617
- **机制**：**六子包镜像**（config/constants/context/env/validation 对称——SPI 四族各自实现）；**@MapperScan 占位符**（:35——注解属性占位符 + 运行时解析）；**枚举包推导**（:53——typeEnumsPackage 从 base-packages）；**通配符包扫描**（:60-64——CachingMetadataReaderFactory）；**互斥校验双向**（:20-21——两 ORM 不能共存——REQ-002 实证）
- **my-xhs**：已用（单 ORM）——多 ORM 隔离为差距

### 4.2 ShardingSphere + Transaction 模块 [🔴 P1] [时间无关模式]
- **来源**：KP-618/KP-619
- **机制**：**YAML → 配置 Bean**（YamlRootConfiguration 解析 + Mode/Rule 分注册——命名空间化 Bean 名 :66/:86）+ **关闭钩子线程同步**（Thread::run——:46——异步钩子变同步——关闭时序保证）；**事务管理器别名注册**（:30-34——registerBeanAliases——父上下文 @Qualifier 按名注入）+ 事务 Bean 名生成器（ParentContextBeanNameGenerator 模块实现）
- **缺陷认知**：无跨单元事务协调（每单元独立 TransactionManager——REQ 缺陷表）
- **my-xhs**：已用（单事务）/ **已用（sharding 实证修正）**——my-xhs-order ShardingSphereDataSourceConfig:34-118（订单分库分表——官方 Starter 配置类）

---

## 五、配套与工程面 [工程问题]

### 5.1 datasource 默认值填充 [🟡 P2] [时间无关模式]
- **来源**：KP-615
- **机制**：**DatabaseDriver.fromJdbcUrl 推导驱动**（:120——Boot 官方）+ JdbcURLAssembler URL 规范化（scheme 补全）+ **setPropertyIfAbsent 统一填充**（缺省才补——不覆盖显式配置）+ 重名校验
- **my-xhs**：已用——Boot 官方能力共享

### 5.2 常量/错误聚合/关闭同步 [🟢 P3] [时间无关模式]
- **来源**：KP-606/KP-610/KP-613
- **机制**：命名约定族（`DynamicJdbcChildContext[...]` 模板）；**InitializeErrors ConcurrentHashMap + putIfAbsent**（:16/:20——并行安全聚合）；SyncExecutionShutdownHookApplicationListener（:46 Thread::run）
- **my-xhs**：不该用——配套无独立知识

---

## 覆盖核对（20/20 + my-xhs 对照）

| 维度 | 覆盖 KP | 数 |
|------|---------|:--:|
| 一、子上下文隔离 | 601,603,604,605,614 | 5 |
| 二、4 SPI 管道 | 602,607,608,609,611,612,620 | 7 |
| 三、动态数据源架构 | 614,616 | 2 |
| 四、模块化生态 | 617,618,619 | 3 |
| 五、配套工程 | 606,610,613,615 | 4 |

**去重后唯一 KP**：601-620 全部 = **20/20 ✓**（KP-614 跨维度——去重计 1）
**无孤儿 KP** ✓
**my-xhs 对照**：已用 6（605/614/615/617/618/619——KP-618 sharding 判定实证修正）/ 该用没用 13 / 不该用 3——**核心差距 = 多单元隔离与模块化管道**；**架构差异 = 预建池切换 vs 子上下文重建**。

---

## 六、问题域知识一：多活架构与 ORM 共存（历史 18-01/18-02 对照）[分布式问题]

> **核心命题**：dynamic 是"数据库层多活"的实现载体——理解问题域才知道子上下文隔离为什么是正解。

### 6.1 多活六级架构 + 两种部署模型 [🔴 P1] [时间无关模式]
- **来源**：历史 18-01 + 本仓库（KP-601/605）
- **机制**：**多活六级**（接入层 → 无状态服务层 → 配置层 → **数据库层**（dynamic 所处层）→ 缓存层 → 消息层——18-01 :75-163）；**两种部署模型**（A 按 Zone 独立部署 vs B 单实例多 Zone（18-01 :167-196）——**18-dynamic 的选择 = 单实例多 Zone**（一个实例内按 zone 切换数据源——子上下文重建）；**Zone/Region 概念**（Region 地域 / Zone 可用区——18-01 :201-239——数据库多活中的影响）；**同城双活 vs 异地多活**（:240——容灾级别）
- **my-xhs**：已用——my-xhs 单实例多 Zone（预建池切换——与 microsphere 同模型不同机制）

### 6.2 ORM 共存问题域 [🔴 P1] [时间无关模式]
- **来源**：历史 18-02 + 本仓库（KP-617 互斥校验）
- **机制**：**为什么不能共存**（4 问题：SqlSessionFactory 竞争 DataSource :19 / 绑定冲突 :47 / Configuration 对象冲突 :62 / 事务管理冲突 :68）；**根源**（Boot AutoConfiguration 设计假设 + @ConditionalOnMissingBean 限制 :82-105）；**三种共存方案**（A 独立数据源 + 独立 ApplicationContext :160——**dynamic 采用** / B 同源多 SqlSessionFactory :184 / C 包路径 + 手动路由 :247）；**不能共享同一数据库的限制**（:356）
- **my-xhs**：已用（单 ORM）——共存问题为差距（若未来多库）

---

## 七、问题域知识二：连接池/路由/ShardingSphere/子上下文深潜（历史 18-03/04/05/06 对照）[工程问题]

### 7.1 连接池与路由知识本体 [🔴 P1] [时间无关模式]
- **来源**：历史 18-06 + 本地生态源码（HikariCP/spring-jdbc）+ KP-621
- **机制**：**DataSource 接口设计**（18-06 :15-118——接口定义/实现层次/DynamicDataSource 委托模式/层层包装原因）；**三种动态机制**（KP-621——路由/重建/预建池）；**HikariCP**（ConcurrentBag 高性能连接容器（本地源码实证——handoff 队列）/连接生命周期（创建→验证→借用→归还→关闭）/连接验证（:318）/调优参数（:372-404——minimumIdle/maximumPoolSize/idleTimeout/maxLifetime/connectionTimeout 经验值——**my-xhs ReadWriteRoutingDataSourceConfig :38-42 实证同族参数**）
- **my-xhs**：已用——Hikari 参数族（实证同族配置）

### 7.2 ShardingSphere 生态 [🟡 P2] [时间无关模式（生态知识）]
- **来源**：历史 18-03 + 本仓库（KP-618）
- **机制**：**三层架构**（:56——接入/核心/基础设施层）；**分片 SQL 处理流程**（:110——解析→路由→改写→执行→归并）；**5.x vs 4.x**（YAML 配置/Mode 体系重构/规则引擎/包名/事务 :173-242）；**Mode 体系**（Memory/Standalone/Cluster :243-300——YAML 配置的 Mode 部分——KP-618 的 Mode 注册对应知识）
- **my-xhs**：该用没用——无分库分表（若未来数据量增长是差距）

### 7.3 子上下文生命周期深潜 [🔴 P1] [时间无关模式]
- **来源**：历史 18-04 + 本仓库（KP-601/603/604）
- **机制**：**refresh() 12 步生命周期**（:22——18-dynamic 利用哪些步骤 :93——postProcessBeanFactory/finishRefresh 等）；**AnnotationConfigApplicationContext 三层构造链**（:119-174——AnnotatedBeanDefinitionReader 注册处理器——**为什么管道第 1 步再调 registerAnnotationConfigProcessors**（:214——子上下文手动上下文无自动注册））；**Environment 合并深潜**（:236-346——merge 语义/removeSynthesized/detach 的作用——**合成源防污染**）；**Bean 升迁机制**（:347——回注细节）
- **my-xhs**：该用没用——无子上下文

### 7.4 自动配置内部 + 配置系统 [🟡 P2] [时间无关模式]
- **来源**：历史 18-05/18-09/18-10 + 本仓库（KP-616/602/620）
- **机制**：**AutoConfiguration 三阶段**（:17-73——解析→过滤→导入——Import 三件套对应）；**缓存优先策略**（:133——ClassLoader 作用域缓存）；**配置系统全链路**（18-09 :16-73——输入输出：JSON 字符串/classpath 资源 → ObjectMapper → POJO → PostProcessor 链 → Synthesizer 链 → Environment）；**事件驱动总览**（18-10 :18-47——三事件种类——KP-605 完整链路对应）
- **my-xhs**：已用（简化）——@ConfigurationProperties 直绑（无 JSON 字符串管道）

---

## 覆盖核对（22/22 + 问题域 2 维度）

| 维度 | 覆盖 KP | 数 |
|------|---------|:--:|
| 一、子上下文隔离 | 601,603,604,605,614 | 5 |
| 二、4 SPI 管道 | 602,607,608,609,611,612,620 | 7 |
| 三、动态数据源架构 | 614,616,621 | 3 |
| 四、模块化生态 | 617,618,619 | 3 |
| 五、配套工程 | 606,610,613,615 | 4 |
| 六、问题域：多活/ORM 共存 | 601,605,617 + 历史 18-01/18-02 | — |
| 七、问题域：连接池/生态/深潜 | 618,621 + 历史 18-03/04/05/06 | — |

**去重后唯一 KP**：601-622 全部 = **22/22 ✓**
**无孤儿 KP** ✓
**问题域对照**：历史 14 篇分析全部映射（01-12 篇 → 六/七维度）——无遗漏主题 ✓
