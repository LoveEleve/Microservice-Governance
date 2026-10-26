# Microsphere Observability 可观测性增强知识大纲（observability 触发面）

> 来源：`mapping/11-microsphere-observability.md` 4 个 KP 的知识本体提炼
> 性质：**源码侧 outline（L1.5）**——mapping 是文件映射（过程），本大纲是知识本体（结果）
> 组织：按知识维度，非按文件；每个知识点标注来源 KP + 六元元数据 + my-xhs 落地实证
> 用途：①自学/面试知识图谱（可观测性/事件时序陷阱）②与课程 L1 合并成 L3 的源码侧素材 ③my-xhs 对照
> 覆盖核对：4/4 KP 全部归属（文末核对表）

---

## 一、启动期日志缓冲与事件时序陷阱（核心命题）[工程问题]

> **核心命题（P1 教学案例）**：自动配置 Bean 的 `@EventListener` 在 **Bean 实例化后才注册**——监听**早期事件（ApplicationPreparedEvent——refresh 前发布）= 永不触发**；必须用**晚期事件（Started/Ready）**。

### 1.1 缓冲-转移模式 + P1 时序 bug [🔴 P1] [时间无关模式]
- **来源**：KP-1001
- **机制**：**InMemoryAppender 缓冲**（:39——ConcurrentSkipListSet 有序集合 :46——**数据结构自然性质换转移时间一致性**）+ **transfer 转移**（:98）；**时序链**（Adding 监听 Prepared :36（spring.factories 原生监听——必触发）+ Removing 监听 Started :36）；**P1**：KafkaAppenderConfiguration `@EventListener(ApplicationPreparedEvent)`（:85——**自动配置 Bean 早期事件不触发**——Kafka Appender 永不挂载——日志静默丢弃）——**正确对照**（ApplicationLoggingAutoConfiguration :39 Started——同项目正反对照）
- **第二丢失路径**：LogEventComparator 仅毫秒比较（:41-42）——SkipList 同毫秒去重
- **时序铁律**：**自动配置监听早期事件 = 永不触发**（早期事件用 spring.factories 原生监听器；自动配置 Bean 用晚期事件）
- **my-xhs**：该用没用——logback 覆盖；时序铁律直接适用（警示）

---

## 二、框架无关抽象与桥接 [工程问题]

### 2.1 Filter 抽象 + Log4j2 适配 [🟡 P2] [时间无关模式]
- **来源**：KP-1002
- **机制**：**框架无关 Filter 族**（Filter/AbstractFilter/CompositeFilter/LoggingNameFilter）+ **Log4j2FilterAdapter 桥接**（微球 Filter → Log4j2）+ i18n 日志（I18nLogger——只完成 trace() 其余空方法——待深读）
- **my-xhs**：不该用——logback filter 覆盖

---

## 三、Micrometer 生态补全 [性能优化]

### 3.1 系统/JMX/Sentinel/JDBC binder + Prometheus [🟡 P2] [时间无关模式]
- **来源**：KP-1003
- **机制**：**容器内存探测**（@ConditionalOnResource cgroup 文件 :22——K8s 内存限制感知）+ **系统指标**（SystemMemory/NetworkStatistics/CGroupMemory）+ **JMX MBean 指标化**（MBeanAttributeMeterBinder）+ **Sentinel 指标**（SentinelMetrics + Prometheus Collector）+ **JDBC 事件指标**（P6Spy LoggingEventListener :41）+ **Prometheus 导出/推送网关条件链**（:42-44）
- **my-xhs**：已用（基础）——actuator + micrometer 官方 Prometheus；CGroup 指标可借鉴

---

## 覆盖核对（4/4 + 对照）

| 维度 | 覆盖 KP | 数 |
|------|---------|:--:|
| 一、缓冲与时序陷阱 | 1001 | 1 |
| 二、抽象与桥接 | 1002 | 1 |
| 三、Micrometer 补全 | 1003 | 1 |
| 四、配套 | 1004 | 1 |

**去重后唯一 KP**：1001-1004 全部 = **4/4 ✓**
**无孤儿 KP** ✓
**历史对照**：P1 三重验证 + 第二丢失路径 + 正确对照 3/4 证实（I18nLogger 待深读）
**my-xhs 对照**：已用 1 / 该用没用 1 / 不该用 2——**P1 时序教训 = 最大可迁移知识**。

---

## 四、问题域知识（历史 09 篇 4 篇对照）[工程问题]

### 4.1 静默失效家族（P1 时序 + P2 语义 + G15 无日志）[🔴 P1] [时间无关模式]
- **来源**：KP-1005（历史 09-01/09-03）
- **机制**：**P2 @ConditionalOnBean 语义陷阱**（:42 类全限定名传 name——官方 bean 名 micrometerClock（MetricsAutoConfiguration:54 本地实证）——**name=bean 名/value=类**——条件永不匹配——Prometheus 导出永久失效）；**earlyApplicationEvents 机制**（为何没救到 @EventListener——注册时机）；**静默失效三模式**（P1 时序/P2 语义/G15 无日志——**无报错生产难发现**）
- **检测规则**（历史 09-03 :169——可迁移）：条件注解 name 参数必须与 bean 名一致（方法名）；自动配置 @EventListener 用晚期事件
- **my-xhs**：该用没用——自写条件注解/事件监听的警示规则

### 4.2 死代码与未完成清单 [🟡 P2] [时间无关模式]
- **来源**：KP-1005（历史 09-01/09-02）
- **机制**：MBeanAttributeMeterBinder 接口骨架无实现（:39）+ CompositeFilter TODO（:36）——**接口先行无实现的"设计文档式代码"**；Sentinel 两实现两隐患 + P6Spy 两问题（待深读）
- **my-xhs**：不该用——无独立落地

---

## 覆盖核对（5/5 + 对照）

| 维度 | 覆盖 KP | 数 |
|------|---------|:--:|
| 一、缓冲与时序陷阱 | 1001 | 1 |
| 二、抽象与桥接 | 1002 | 1 |
| 三、Micrometer 补全 | 1003 | 1 |
| 四、配套 | 1004 | 1 |
| 五、问题域知识 | 1005 | 1 |

**去重后唯一 KP**：1001-1005 全部 = **5/5 ✓**
**无孤儿 KP** ✓
**历史对照**：09 篇 4 篇全映射（P1/P2 双 bug/死代码/测试缺陷/发散设计）——无遗漏主题 ✓
