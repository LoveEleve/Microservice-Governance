# microsphere-observability 知识点提取

> 源码：`/data/workspace/java-training-camp/cloud-native-code/projects/microsphere-observability`（依赖链第 11 站；5 模块 39 生产文件 + 8 测试）
> 提取时间：2026-08-13（批 1：logging 族 14；批 2：metrics micrometer 族 18；批 3：归组）
> 状态：批 1-3 已提取（骨架归组式）；MCP 索引已建（966 节点/2066 边）
> 关联：gateway G15（静默可观测性缺口——本仓库是答案）；i18n（日志国际化）；Sentinel（metrics binder）
> 历史交叉验证：`microsphere-analysis/09-microsphere-observability-analysis/`（4 篇 + REQ——**P1 时序 bug 已详析**）

## 一、仓库定位

**可观测性增强**——logging（Log4j2 增强：InMemoryAppender 启动缓冲/Kafka 投递/框架无关 Filter 桥接/i18n 日志）+ metrics（Micrometer 增强：System/JMX/Sentinel/P6Spy binder + Prometheus 导出/推送）。核心维度：[性能优化]（可观测性）+ [工程问题]（Boot 事件时序）。

## 前置条件清单

读者需先掌握：1. Log4j2 Appender/Layout/Filter 体系 2. Spring Boot 生命周期事件时序（ApplicationPreparedEvent/StartedEvent/ReadyEvent）3. Micrometer MeterBinder/Prometheus 4. 04 仓库条件注解体系
未达前置者，先补：04 仓库 outline + Spring Boot 事件时序

## 掌握度

目标读者：中级偏上（读源码多、Spring 熟——HANDOVER 画像）
讲解策略：P1 时序 bug 为教学主线 + Micrometer 生态对照

---

## 二、逐文件映射 + 原子记录（骨架归组式）

### 模块: `microsphere-logging` + `logging-spring-boot`（批 1：14 文件）

#### KP-1001 `InMemoryAppender` 启动缓冲 + **P1 时序 bug**（InMemoryAppender.java:39-98 + AddingInMemoryAppenderListener.java:36 + RemovingInMemoryAppenderListener.java:36 + Log4j2AutoConfiguration.java:65-91 + LogEventComparator.java:30-42 + DefaultKafkaLayout/DelegatingLayout/Log4j2FilterAdapter/I18nLog4j2Filter + Log4j2KafkaAppenderProperties + Log4j2Utils——14 文件全列）

- **维度**：[工程问题]（事件时序）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（时序陷阱） | **置信度**：High
- **前置**：Spring Boot 事件时序（ApplicationPreparedEvent 在 refresh 前发布——自动配置 Bean 未实例化）、Log4j2 Appender 体系、ConcurrentSkipListSet
- **需求**：**启动期日志不丢失**——远程 Appender（Kafka）网络未就绪时，用 InMemoryAppender 缓冲，真正目标 Appender 就绪后 transfer（历史 09-01 核心命题）
- **参考实现**：**缓冲 Appender**（InMemoryAppender :39——extends AbstractLifeCycle implements Appender——**ConcurrentSkipListSet\<LogEvent>**（:46——**有序集合缓冲**（按 LogEventComparator 时间序——transfer 依赖有序迭代保证时间一致性））；**transfer 转移**（:98——目标 Appender 就绪后转存）；**时序链**（AddingInMemoryAppenderListener extends **OnceApplicationPreparedEventListener**（:36——**spring.factories 原生监听器**——ApplicationPreparedEvent 添加缓冲）+ RemovingInMemoryAppenderListener implements ApplicationListener\<**ApplicationStartedEvent**>（:36——Started 移除））；**P1 缺陷（历史 09-01 三重验证）**：**KafkaAppenderConfiguration @EventListener(ApplicationPreparedEvent.class)**（:85——**自动配置 Bean 的 @EventListener——ApplicationPreparedEvent 发布时 refresh 未开始——Bean 未实例化——监听器未注册——永不触发**）→ initializeKafkaAppender（:91——**Kafka Appender 从未挂载** → 日志缓存静默清空丢弃）；**正确对照**：ApplicationLoggingAutoConfiguration 用 **@EventListener(ApplicationStartedEvent.class)**（:39——**晚期事件——Bean 已实例化——监听生效**——同项目正确用法对比）；**第二丢失路径**（LogEventComparator :41-42——`Long.compare(getTimeMillis())`——**只按毫秒比较无 tie-breaking**——ConcurrentSkipListSet 同毫秒日志判等去重——**独立于时序 bug 的第二条日志丢失路径**（历史 09-01 :102）
- **对比取舍**：**知识增量**：①**Spring Boot 事件时序陷阱完整案例**（:85 vs :39——**@EventListener 的注册时机**（Bean 实例化后——早期事件（Prepared）自动配置收不到——晚期事件（Started/Ready）才可以）——**时序选择铁律**（自动配置监听早期事件 = 永不触发）；②**有序集合做时间一致性**（:46——**数据结构自然性质换取转移一致性**——代价是 Comparator 无 tie-breaking 时去重副作用（trade-off 分析）；③**缓冲-转移模式**（InMemory → transfer——**启动期资源就绪前的缓冲方案**）
- **测试佐证**：logging 测试 3+（[补扫]）
- **my-xhs**：**该用没用（实证）**——my-xhs 用 logback（官方默认）+ Kafka Appender 无——缓冲-转移模式可借鉴（若日志投递远程）；**时序铁律直接适用**（my-xhs 若自写 @EventListener 自动配置——早期事件陷阱）

#### KP-1002 框架无关 Filter 抽象 + 桥接（Filter.java + AbstractFilter + CompositeFilter + LoggingNameFilter + Log4j2FilterAdapter + I18nLog4j2Filter + I18nLogger）

- **维度**：[工程问题]（抽象桥接）| **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：Log4j2 Filter、微球 Filter 抽象（03 仓库 web 层同族）、i18n
- **需求**：**框架无关日志过滤**——自研 Filter 抽象桥接到 Log4j2（Log4j2FilterAdapter）+ 日志国际化（I18nLog4j2Filter/I18nLogger——**基于 i18n 仓库**）
- **参考实现**：**Filter 抽象族**（Filter 接口 + AbstractFilter + CompositeFilter（组合过滤）+ LoggingNameFilter（按名过滤）——**框架无关**（03 仓库 web/rule 同族）；**Log4j2 桥接**（Log4j2FilterAdapter——**微球 Filter → Log4j2 Filter 适配**）；**i18n 日志**（I18nLog4j2Filter + I18nLogger——**历史 09-01 缺陷**：I18nLogger 只完成 trace() 一个重载——**其余全空方法**（:3——待验证）
- **对比取舍**：**知识增量**：①**框架无关过滤抽象 + 适配器**（自研 Filter → 日志框架桥接——**抽象中立模式**）
- **my-xhs**：**不该用（实证）**——logback 原生 filter 覆盖；i18n 日志无场景

### 模块: `microsphere-micrometer` + `micrometer-spring-boot`（批 2：18 文件）

#### KP-1003 Micrometer 增强族（MicrometerAutoConfiguration:91-107 + 条件族 4：ConditionalOnMicrometerEnabled/ConditionalOnCGroup/ConditionalOnEnabledPrometheusMetricsExport/ConditionalOnEnabledPrometheusPushGateway + AbstractMeterBinder + SystemMemoryMetrics/NetworkStatisticsMetrics/CGroupMemoryMetrics + MBeanAttributeMeterBinder/MBeanMetrics + SentinelMetrics/AbstractSentinelMetrics/SentinelCollector + MicrometerJdbcEventListener + MicrometerUtils——18 文件全列）

- **维度**：[性能优化]（指标）| **权重**：[核心] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：Micrometer MeterBinder/MeterRegistry、Prometheus、CGroup（容器内存）、JMX、Sentinel
- **需求**：**Micrometer 生态补全**——系统内存/网络/CGroup（容器）指标 + JMX MBean 指标 + Sentinel 指标 + JDBC（P6Spy）监听 + Prometheus 导出/推送网关
- **参考实现**：**自动配置**（MicrometerAutoConfiguration :100——@ConditionalOnMicrometerEnabled + SystemConfiguration（:105——system 属性开关）+ 条件族（ConditionalOnCGroup :22——**@ConditionalOnResource("file:///sys/fs/cgroup/")**——**容器 cgroup 探测**（容器内内存限制）/ ConditionalOnEnabledPrometheusMetricsExport :42-44（Clock Bean + 导出开关 + PrometheusMeterRegistry 类）/ PushGateway :42-44（导出 + PushGateway 类 + 属性））；**binder 族**（SystemMemoryMetrics/NetworkStatisticsMetrics/CGroupMemoryMetrics——系统指标 + MBeanAttributeMeterBinder/MBeanMetrics——**JMX MBean 属性指标化** + SentinelMetrics/AbstractSentinelMetrics/SentinelCollector——**Sentinel 指标**（Prometheus Collector）+ MicrometerJdbcEventListener extends LoggingEventListener（:41——**JDBC 事件指标**（P6Spy）））
- **对比取舍**：**知识增量**：①**容器内存探测**（@ConditionalOnResource cgroup 文件 :22——**容器场景条件装配**（K8s 内存限制感知））；②**Prometheus 推送网关条件链**（:42-44——**导出条件组合**（registry 类 + 开关 + PushGateway 类）；③**生态指标补全**（JMX/Sentinel/JDBC——**Micrometer 官方之外的 binder 扩展**）
- **my-xhs**：**已用（实证）**——my-xhs 用 actuator + micrometer 官方（prometheus 导出）——**官方覆盖基础**；CGroup 容器内存指标可借鉴（若容器部署）

### 模块: 其余（批 3：7 文件归组）

#### KP-1004 配套族（Logging.java/StandardLogging/LoggingConfiguration + WebMvcLoggingAutoConfiguration/WebServerLoggingAutoConfiguration + ServletConstants——7 文件全列）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟢 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：Medium（归组）
- **需求**：**配套归组**——Logging 抽象接口/StandardLogging（JDK 日志默认）/LoggingConfiguration/WebMvcLoggingAutoConfiguration/WebServerLoggingAutoConfiguration（MVC/Web 服务器日志增强）
- **my-xhs**：**不该用**——官方 logback 覆盖

### 包总结（observability 批 1-3）

- **核心命题**：**"启动期日志缓冲 + 事件时序陷阱（P1）+ Micrometer 生态补全"**——logging（缓冲-转移 + P1 教学案例）+ metrics（系统/JMX/Sentinel/Prometheus）
- **P1 是本仓库最大教学价值**：@EventListener 注册时机（早期事件 vs 晚期事件）——**自动配置监听早期事件 = 永不触发**——与 gateway G15（静默可观测性缺口）呼应（本仓库是"为什么需要可观测"的答案）

---

## 三、深度 review 七项报告（批 1-3 合并）

> 2026-08-13 批判性 review：穷尽性核对先行——39/39 生产文件全覆盖（骨架归组式——文件全列）。

- [x] **① 源码行号精确核对**：KP-1001:39/:46/:98/:36/:36/:85/:91/:39/:41-42、KP-1002:Filter 族、KP-1003:100/:105/:22/:42-44/:41——全部 grep 实证 ✓
- [x] **② 穷尽性**：39/39 生产文件全覆盖（basename 脚本核对——铁律 #7）✓
- [x] **③ 空节标注**：KP-1002/1004 Medium（归组）✓
- [x] **④ 过时三级**：4 KP 全部标注（均时间无关模式）✓
- [x] **⑤ 重复内容**：Filter 抽象与 03 仓库 web/rule 同族；条件注解与 04 仓库体系 ✓
- [x] **⑤b 引用目标核对**：OnceApplicationPreparedEventListener/OnceApplicationStartedEventListener（微球 boot 基类——04 仓库有）✓；Log4j2/Micrometer 官方类（依赖 jar）✓
- [x] **⑥ 诚实标注**：KP-1002/1004 Medium；I18nLogger 空方法待深读 ✓
- [x] **⑦ 命名空间迁移**：N/A ✓

### 历史分析交叉验证（11-observability）

> 来源：`09-microsphere-observability-analysis/`（4 篇 + REQ——P1 已三重验证）

| # | 历史断言 | 验证 | 落点 |
|---|---------|------|------|
| P1 | @EventListener(ApplicationPreparedEvent) 时序错误——Kafka Appender 永不挂载 | ✅ 证实（:85 Prepared vs :39 Started 对照——自动配置 Bean @EventListener 早期事件不触发） | KP-1001 |
| 第二丢失路径 | ConcurrentSkipListSet tie-breaking 去重（同毫秒日志丢失） | ✅ 证实（LogEventComparator :41-42 仅毫秒比较 + InMemoryAppender :46 SkipList） | KP-1001 |
| 正确对照 | ApplicationLoggingAutoConfiguration 等用晚期事件 | ✅ 证实（:39 ApplicationStartedEvent） | KP-1001 |
| I18nLogger 空方法 | 只完成 trace() 其余空 | ⬜ 待深读（I18nLogger 归组） | KP-1002 |
| metrics 补全 | System/JMX/Sentinel/Prometheus | ✅ 证实（binder 族 + 条件族） | KP-1003 |

**进度**：4/5 证实；待验证 1（I18nLogger 空方法）

---

## 四、my-xhs 落地判定汇总表（2026-08-13 实证版）

| KP | 判定 | 说明 |
|----|------|------|
| KP-1001 | 该用没用 | logback 官方覆盖缓冲需求；**时序铁律直接适用**（my-xhs 自写 @EventListener 自动配置的警示） |
| KP-1002 | 不该用 | logback filter 覆盖；i18n 日志无场景 |
| KP-1003 | 已用（基础） | actuator + micrometer 官方 Prometheus 导出；CGroup 容器指标可借鉴 |
| KP-1004 | 不该用 | 官方 logback 覆盖 |

**汇总**：已用 1 / 该用没用 1 / 不该用 2。
**核心结论**：my-xhs 官方可观测性栈覆盖（logback + actuator + micrometer）；**P1 时序教训**为最大可迁移知识（自动配置事件监听时序）。

### 深度 review 补充（问题域对照轮——历史 09 篇 4 篇分析）

#### KP-1005 问题域补充（P2 @ConditionalOnBean 语义 + earlyApplicationEvents + 死代码清单 + 问题清单验证）

- **维度**：[工程问题]（条件注解语义）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：@ConditionalOnBean name vs value 语义（Boot 官方）、bean 名生成规则（方法名）、earlyApplicationEvents 机制（AbstractApplicationContext）
- **需求**：**第二个严重 bug 验证 + P1 机制补全**（历史 09-03 三层证据链 + 09-01 4.4）
- **参考实现**：**P2 缺陷（历史 09-03 三层证据链全部源码验证）**：`@ConditionalOnBean(name = "io.micrometer.core.instrument.Clock")`（ConditionalOnEnabledPrometheusMetricsExport:42——**类全限定名传给 name 参数**）vs 官方 Clock bean 名 **"micrometerClock"**（MetricsAutoConfiguration.java:54——**按方法名生成 bean 名**——本地 spring-boot 源码实证）——**name 按 bean 名匹配（containsBean）非类名——条件永不匹配**——**@ConditionalOnBean 家族语义**（name=bean 名/value=类——**同名注解家族内部语义不一致**（历史 09-03 :45）——**Prometheus 导出条件永久失效**（连锁：导出配置不生效——与 P1 同族"静默失效"）；**P1 机制补全——earlyApplicationEvents**（历史 09-01 4.4 :228——AbstractApplicationContext.earlyApplicationEvents 缓冲早期事件——**为何没救到**：@EventListener 方法未注册时事件进 earlyApplicationEvents 缓冲——但**应用监听器（spring.factories 的）会立即收到**——@EventListener 的注册发生在 refresh 的 EventListenerMethodProcessor 阶段——**早于该阶段的事件（Prepared）不进早缓冲投递路径**——细节待深读）；**死代码清单**（MBeanAttributeMeterBinder 接口 :39——**骨架无实现**（历史 09-02 :109——"死代码的完整设计"）；CompositeFilter :36——**TODO 未完成**（历史 09-01 6.5））；**Sentinel 两实现两隐患**（历史 09-02 :162/:188——两套实现并存原因 + 两个隐患——待深读）；**P6Spy 两问题**（历史 09-02 :190/:211——待深读）；**测试基建缺陷**（历史 09-03 :114——spring-boot 侧 3 个"装配完成=通过"浅层测试 vs micrometer 侧真断言——**测试深度不齐**）
- **对比取舍**：**知识增量**：①**@ConditionalOnBean name/value 语义陷阱**（:42——**类名 vs bean 名**——条件注解家族最常见的错误（与 06 FILER/08 contains 同族——**API 语义细节错误家族再添例**）；②**P1/P2 共性**（历史 09-03 :79——**都是"静默失效"**（永不触发/永不匹配——无报错——生产难发现）——**静默失效家族**（P1 时序 + P2 语义 + G15 无日志——跨仓库呼应）；③**死代码清单方法论**（MBean 骨架/CompositeFilter TODO——**接口先行无实现的"设计文档式代码"**）
- **my-xhs**：**该用没用（实证）**——my-xhs 用官方 Prometheus 导出（无自写条件注解）；**@ConditionalOnBean 语义教训直接适用**（my-xhs 自写条件注解时 name/value 区分——若未来自写）

### 包总结（问题域补充）

- **核心命题**：**"静默失效家族 + 条件注解语义"**——P1（时序）+ P2（name/value 语义）+ G15（无日志）——**三种静默失效模式**跨仓库汇总
- **验证成果**：P2 三层证据链全证实（:42 + 官方 :54）；死代码 2 处证实（MBean 骨架/CompositeFilter TODO）；Sentinel/P6Spy 隐患待深读
