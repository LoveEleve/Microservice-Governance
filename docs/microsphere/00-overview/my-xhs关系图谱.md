# my-xhs × L3 总大纲关系图谱

> **定位**：探索 my-xhs 项目与 L3 总教学大纲的关系——**"知识 → 落地"闭环的实证载体**
> 探索方法：代码级对照（microsphere 源码 ↔ my-xhs 代码 ↔ L3 主题判定）
> 日期：2026-08-15

---

## 一、核心关系：my-xhs = L3 的"落地卷"

**L3 的教学闭环**（方向规划定稿）：模式本体 → 现代载体 → **my-xhs 判定（已用/该用没用/不该用——代码实证）** → 实验断言。

**my-xhs 在 L3 中的三重角色**：
1. **实证源**——33 篇的"my-xhs 判定"全部是 my-xhs 真实代码（行号实证）
2. **落地载体**——microsphere 的"提取→重写→上真线"闭环的接受方
3. **差距清单**——"该用没用"判定汇总成可执行清单（P1/P2/P3）

---

## 二、移植对照：microsphere → my-xhs（代码级实证）

### 2.1 整体移植案例（Zone 机制——2.8 章节最大实证）

| 微球（multiactive-commons zone 6 类） | my-xhs（zone 17 文件） | 关系 |
|------|------|------|
| `ZoneContext`（instance :34 + 7 volatile 属性） | `zone/ZoneContext.java`（INSTANCE :30 + 7 volatile 属性**逐字段对应**） | **移植 + 命名现代化** |
| `ZoneConstants`（常量接口 :15） | `ZoneConstants.java` + `ZoneProperties.java`（@ConfigurationProperties 嵌套） | **现代化改写**（常量接口→Boot 绑定） |
| `ZonePreferenceFilter`（三重保护） | `zone/ZonePreferenceFilter.java` | **逐行同构移植** |
| `ZoneResolver`（泛型多实体） | `zone/ZoneResolver.java` + `loadbalancer/ServiceInstanceZoneResolver.java` | 移植 + 场景化（Nacos 单源） |
| `ZoneAutoConfiguration`（SpringFactories 双通道） | `ZoneContextAutoConfiguration`（@ConfigurationProperties 单层条件） | 现代化改写（场景化瘦身） |
| —（微球没有） | `zone/loadbalancer/ZonePreferenceServiceInstanceListSupplier.java` + `ZoneLoadBalancerConfiguration` | **自主扩展**（LoadBalancer 集成） |
| —（微球没有） | `zone/redis/` 7 文件（命令拦截/事件化） | **自主扩展**（Redis 多活可观测） |
| —（微球没有） | `zone/datasource/DynamicDataSource.java`（zone 变更→数据源切换） | **自主扩展**（存储侧联动） |

**结论**：my-xhs 不是复制 microsphere——**移植核心（10 KP）+ 现代化改写（@ConfigurationProperties/@Slf4j/命名）+ 场景化瘦身（单源 Nacos）+ 自主扩展（Redis/DataSource 族）**——"知识本体的三种处理方式"实证。

### 2.2 其他移植/对应案例

| microsphere 源码 | my-xhs 对应 | L3 落位 |
|------|------|:--:|
| 10 仓 `EventPublishingRedisCommandInterceptor` | `zone/redis/interceptor/EventPublishingRedisCommandInterceptor.java`（同名移植） | 2.10/2.6 |
| 06 仓 `ZoneContextChangedEvent` 桥接 | `zone/redis/` 事件族 + `DynamicDataSource` 原生 PropertyChangeListener | 2.8 |
| 07 仓 `DynamicDataSource`（子上下文重建） | `zone/datasource/DynamicDataSource.java`（**原生监听切换——比微球更直接**） | 2.6/2.8 |
| 01 仓 JDK 工具（confucius） | **不移植**——JDK 原生覆盖（List.of 220+/ProcessHandle） | 4.7 |
| 13 仓 Druid 连接池增强 | **不移植**——Hikari + Boot 官方池指标（同机制） | 5.4 |
| 09 仓 Filter 链缓存 | **自主同构实现**——`gateway/handler/CachingFilteringWebHandler.java`（规避 G8 坑） | 5.1 |

---

## 三、技术栈映射：my-xhs 技术点 → L3 主题

| my-xhs 技术 | 模块/包 | L3 主题 | 判定 |
|------|------|:--:|:--:|
| Nacos（discovery+config 双 Starter） | 全局 | 2.1/2.2 | 已用 |
| Spring Cloud LoadBalancer + 3 自研 LB | common/loadbalancer | 2.3 | 已用（自研扩展） |
| Sentinel（Bulkhead/规则下发/网关限流） | 全局 | 2.4 | 已用 |
| TccFenceService（防悬挂） | common/tcc | 2.5 | 已用（TCC 落地） |
| Redis 缓存三兄弟 + Redisson 锁 | common/cache + inventory/coupon | 2.6 | 已用 |
| SCG WebFlux 网关 + 5 个 Filter | gateway | 2.7 | 已用 |
| Zone 机制整体移植 + Redis/DataSource 扩展 | common/zone | 2.8 | 已用（最大案例） |
| Micrometer + SkyWalking + trace 透传 | common/metrics + trace | 2.9 | 已用 |
| RocketMQ（消息 + 幂等 + Outbox） | 各模块 consumer | 2.10 | 已用 |
| CosId（号段）+ 雪花 + Redis 自增 | common/id | 2.6 交叉 | 已用（三合一） |
| XXL-JOB（analytics） | analytics/config | 4.8 交叉 | 已用（2026-08-15 核实） |
| Hikari 读写分离双池 | common/config/ReadWriteRoutingDataSourceConfig | 5.4 | 已用 |
| K8s 部署模板（8 文件） | k8s/ | 1.7 | 部分已用 |
| GracefulShutdown 停机套件 | common/shutdown | 4.7 | 已用 |
| CompletableFuture + MDC 线程池 | order | 5.3 | 已用 |

---

## 四、差距面：该用没用（L3 汇总清单——3 项 P1）

| # | 差距 | L3 落位 | 现状实证 |
|---|------|:--:|------|
| P1-A | RocketMQ 反射整改（官方 getProducer） | 4.7 | RocketMQHealthIndicator.java:89-90 |
| P1-C | JFR 启用 + NMT 开启 | 5.2 | start-all.sh 0 处 |
| P1-D | 命令级限流（Redis/MyBatis） | 2.4/2.6 | 拦截器只做事件化未做限流 |

**已修正的过时判定**（探索过程发现——判定随代码演进更新）：
- Filter 链缓存：该用没用 → **已用**（CachingFilteringWebHandler）
- K8s：该用没用 → **部分已用**（部署模板就绪）
- XXL-JOB：未引入 → **已引入**（XxlJobConfig）

---

## 五、不该用面（my-xhs 主动选择——L3 判定）

| 知识 | my-xhs 选择 | 理由 |
|------|------|------|
| Eureka/Ribbon/Hystrix | 不用 | 过时（Nacos/SCLB/Sentinel 替代） |
| ZK 注册中心 | 不用 | 无依赖（Nacos 覆盖） |
| 自研 SPI 框架 | 不用 | SpringFactories 覆盖 |
| 自研 JDK 工具 | 不用 | JDK 原生覆盖（List.of 220+） |
| 本地 LRU 缓存（Caffeine） | **决策不用** | CounterService 注释实证（多实例一致性） |
| Native/GraalVM | 不用 | Metadata 成本 |
| JPA | 不用 | MyBatis 精确 SQL（用户决策） |

---

## 六、关系总结

```
microsphere（14 仓 230 KP）
  → ① 提取：mapping/outline（L1.5）
  → ② 聚合：L2 模式家族（10 模式 + 5 维度 + 缺陷）
  → ③ 教学：L3 33 篇（书章节——每个 KP 展开）
  → ④ 落地：my-xhs 判定（已用/该用没用/不该用——代码实证）
      ├── 已用：移植（zone 10 KP）+ 自主实现（CachingFilteringWebHandler）
      ├── 该用没用：差距清单（P1 3 项待执行）
      └── 不该用：防误入表（官方覆盖）
  → ⑤ 验证：29 实验模块 140 断言 + my-xhs 真线
```

**一句话**：**my-xhs 是 L3 的"落地卷"**——33 篇每个知识点的终点都是 my-xhs 代码里的一个实证（已用）或清单（差距）；microsphere 是"老师"，L3 是"教材"，my-xhs 是"作业本"。

---

## 七、深化探索（2026-08-15 轮 2——代码级对照）

### 7.1 方法级同构实证（ZonePreferenceFilter）

| 方法 | 微球 | my-xhs | 差异 |
|------|:--:|:--:|------|
| `filter`（七级流程） | ✅ | ✅ | **前 4 级逐行对应**（空/单元素→总开关→偏好开关→无效 zone） |
| `filterDisabledZone` | ✅ | ✅ | 同构 |
| `isUpstreamZoneNotReady` | ✅ | ✅ | **算法完全同构**（`zoneCount * 100 / entitiesSize`——连整数除法语义都保留） |
| `isUnderSameZoneMinAvailableThreshold` | ✅ | ✅ | 同构 |
| `matches` | ❌ | ✅ | **my-xhs 自主补充** |

**结论**：my-xhs 不是"参考了思路"——是**逐行重写的同构移植**（方法签名/算法/流程对应），只做现代化（日志/命名/注释）。

### 7.2 缺陷传播：微球 bug 在 my-xhs 的修正

| 微球缺陷 | my-xhs 处理 | 意义 |
|------|------|------|
| `PREFERENCE_FILER`（:108 拼写） | `PREFERENCE_FILTER`（:33 **已修正**） | "API 稳定约束"教训被吸收 |
| 常量接口（ZoneConstants interface） | ZoneProperties @ConfigurationProperties 嵌套 | 4.7 的常量接口→Boot 绑定知识落地 |
| 测试缺口（微球 Zone 无完整测试） | **ZonePreferenceFilterTest 14 断言** | 补上"没测过的分支"（4.7 的 XOR 教训） |

### 7.3 自主增量（my-xhs 有而 microsphere 没有的——L3 知识落地为代码）

| my-xhs 自主实现 | L3 知识来源 | 说明 |
|------|:--:|------|
| **chaos 包**（ChaosInterceptor——延迟/异常/null 注入） | 2.8（Chaos 5 级递进反模式） | **故障演练知识落地为代码**（enabled=false 零开销设计） |
| **version 包**（ApiVersionCondition——Header 版本 compareTo 选最大） | 4.7（SemVer 版本比较） | **版本兼容知识落地** |
| **spel 包**（SpELParser） | 4.7（表达式） | 规则引擎面 |
| **web/EtagResponseBodyAdvice** | 5.1（Body 缓存面） | ETag 缓存 |
| **tcc/TccFenceService** | 2.5（TCC 防悬挂） | 事务面落地 |
| **id 三合一**（雪花+号段+Redis 自增） | 2.6（分布式 ID 交叉） | CosId 2.6.8 实证 |

### 7.4 业务链路的知识落地（L3 主题 × my-xhs 业务）

| my-xhs 业务点 | L3 知识落地 |
|------|------|
| 网关 8 个 Filter（ApiVersion/BodyCache/Auth/GrayRoute/HmacSignature/RateLimit/RequestLog/TrafficColoring） | 2.7 Filter 三要素 + 2.8 灰度 + 1.7 签名 |
| OrderService:478-481（CompletableFuture + MDC 线程池 + 本地消息表） | 5.3 异步 + 2.5 Outbox |
| CacheHelper:32（空值防穿透 + TTL 随机偏移防雪崩 + 分布式锁防击穿） | **2.6 缓存三兄弟全防** |
| PayStrategyConfig（策略模式） | 4.6 策略模式 |
| CounterBuffer（攒批合并刷盘） | 2.5 Outbox 变体 |
| benchmark 3 个 JMH 类 | 5.2 三工具分工（JMH）——P1-2 真实化差距 |

### 7.5 自定义指标探索（用户指出——cgroup + 动态权重）

| 知识点 | microsphere 侧 | L3 覆盖 | my-xhs 侧 |
|------|------|:--:|------|
| **cgroup 指标** | 源码 11 `CGroupMemoryMetrics`（/sys/fs/cgroup 直读 + 5 字节型 + 11 stat 细分 + @ConditionalOnResource 探测） | **本轮补齐 2.9**（原只有一句提及未展开——用户指出） | 未用（K8s 后容器内存指标为差距——P2-I） |
| **动态权重 LB** | 课程 stage-1-12（WeightedResponseTimeRule 思路） | ✅ 2.3 §3 已覆盖 | sca-lab gateway-lab 落地（weight=1/(avgMs+1) + RingBuffer 滑动窗口 + 冷启动均匀退化 + order>10150 决策） |
| **自定义 binder 族** | SystemMemory/NetworkStatistics/JMX/Sentinel/JDBC（P6Spy） | 本轮补 2.9（一句话带过） | 官方覆盖评估 |

**教训**：用户记忆是"知识图谱的活索引"——cgroup 指标在提取层（outline 11 3.1）完整保留但教学层（2.9）漏展开——**查漏补缺要回听用户的"我记得"**。

### 7.7 测试关系

| 层级 | 数量 | 定位 |
|------|:--:|------|
| L3 实验模块 | 29 模块 140 断言 | **知识验证**（每个知识点能跑） |
| my-xhs 测试 | 59 测试文件（Zone 14 断言等） | **业务验证**（上真线正确性） |

**两层测试的关系**：L3 实验验证"知识本体正确"，my-xhs 测试验证"落地正确"——**同一知识点两道验证**（如 Zone 三重保护：spec-zone 3 断言 + ZonePreferenceFilterTest 14 断言）。
