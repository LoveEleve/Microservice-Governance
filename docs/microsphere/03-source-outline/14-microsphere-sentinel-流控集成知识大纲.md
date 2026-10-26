# Microsphere Sentinel 流控集成知识大纲（sentinel 触发面）

> 来源：`mapping/14-microsphere-sentinel.md` 3 个 KP 的知识本体提炼
> 性质：**源码侧 outline（L1.5）**——mapping 是文件映射（过程），本大纲是知识本体（结果）
> 组织：按知识维度，非按文件；每个知识点标注来源 KP + 六元元数据 + my-xhs 落地实证
> 用途：①自学/面试知识图谱（限流落地架构）②与课程 L1 合并成 L3 的源码侧素材 ③my-xhs 对照
> 覆盖核对：3/3 KP 全部归属（文末核对表）

---

## 一、模板 API 与插件 SPI（核心命题）[工程问题]

> **核心命题**：不是重写 Sentinel——**模板 API 免样板 + 插件 SPI 统一声明 + 6 适配器把流控贴到框架扩展点**——5 类适配器官方没有。

### 1.1 模板 + 插件 + 仓库 [🔴 P1] [时间无关模式]
- **来源**：KP-1301
- **机制**：**SentinelTemplate**（execute 重载族 :74——Runnable/Consumer/Function 免 try/catch/finally + begin() 手控 :64）+ **插件五元组**（name/contextName/origin/resourceType/trafficType :37 + enable/disable :77-84）+ **仓库双实现**（Simple 内存 + **JMX MBean 管理** :45——运行期开关插件）+ **MethodHandle resetContextMap**（:51——02 模式）
- **my-xhs**：已用（部分）——官方 sentinel；模板 API 可借鉴

---

## 二、六适配器统一模式（核心资产）[分布式问题]

### 2.1 插件 + 扩展点双实现 [🔴 P1] [时间无关模式]
- **来源**：KP-1302
- **机制**：**统一架构**（每个适配器 = extends AbstractSentinelPlugin implements {框架扩展点}——MyBatis ExecutorFilter :48/Druid AbstractStatementFilter :46/Redis RedisConnectionInterceptor :50/P6Spy JdbcEventListener :49/Web HandlerMethodInterceptor :59/Hibernate EntityCallback）+ **SentinelOperations 注入**（:50——统一限流入口）+ **两种插件接入**（继承 vs delegate 组合——Druid 因已继承 FilterAdapter 只能组合 :48）
- **跨仓库组合实证**：12/13/10/11 扩展点 = 本仓库挂载点——**提取时的分散知识点在此汇合**
- **my-xhs**：该用没用——**数据库/Redis 级限流为差距**（官方没有）

### 2.2 四重条件装配 [🟡 P2] [时间无关模式]
- **来源**：KP-1303
- **机制**：**条件组合**（Sentinel 可用 + **框架可用（12 仓库条件注解跨仓库消费）** + 开关 + 类存在 :44-47）+ @AutoConfigureAfter + Web 双栈（@ConditionalOnWebApplication(ANY) :29）
- **my-xhs**：该用没用——官方 webmvc 覆盖 Web；数据库/Redis 无

---

## 覆盖核对（3/3 + 对照）

| 维度 | 覆盖 KP | 数 |
|------|---------|:--:|
| 一、模板与插件 | 1301 | 1 |
| 二、适配器统一模式 | 1302,1303 | 2 |

**去重后唯一 KP**：1301-1303 全部 = **3/3 ✓**
**无孤儿 KP** ✓
**历史对照**：07 分析 8 部分主线全映射（模板/插件+JMX/5 扩展点/6 适配器/官方对照）——无遗漏 ✓
**my-xhs 对照**：已用 1 / 该用没用 2——**SQL/Redis 命令级限流 = 官方空白**（微球 5 类独有适配器的落地价值点）。

---

## 三、问题域知识（历史 07 分析正文对照）[分布式问题]

### 3.1 三种上下文传递模式 [🔴 P1] [时间无关模式]
- **来源**：KP-1304（历史 07 :1025-1060）
- **机制**：**模式一 直接包装**（MyBatis 同步链 doInSentinel :63——无传递问题）/ **模式二 pre/post + ThreadLocal**（异步丢失风险——**+08-redis 跨仓库同族**）/ **模式三 框架属性**（Redis/Web——set/getSentinelContext :1040-1046——**异步安全**）
- **选型**：同步链直接包装；异步钩子用框架属性（ThreadLocal 线程池/虚拟线程场景失效）
- **my-xhs**：该用没用——官方 webmvc 适配器（模式三同族）

### 3.2 资源命名基数控制 [🟡 P2] [时间无关模式]
- **来源**：KP-1304（历史 07 :1160-1175）
- **机制**：**裸 SQL 资源名**（P6Spy :93——精确到每条 SQL vs 高基数爆炸）+ **PreparedStatement 过滤缓解**（:105——`?` 占位符基数可控——但同 SQL 多 Mapper 共享资源）；命名粒度对照（Mapper 方法级 vs SQL 文本级）
- **my-xhs**：该用没用——资源命名基数控制直接适用

---

## 覆盖核对（4/4 + 对照）

| 维度 | 覆盖 KP | 数 |
|------|---------|:--:|
| 一、模板与插件 | 1301 | 1 |
| 二、适配器统一模式 | 1302,1303 | 2 |
| 三、问题域知识 | 1304 | 1 |

**去重后唯一 KP**：1301-1304 全部 = **4/4 ✓**
**无孤儿 KP** ✓
**历史对照**：07 分析 8 部分 + 问题域全映射（三模式/高基数/ThreadLocal 风险）——无遗漏 ✓
**跨仓库同族**：ThreadLocal 异步丢失（+08-redis）——第三例
