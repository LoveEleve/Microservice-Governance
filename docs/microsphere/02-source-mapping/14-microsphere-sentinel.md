# microsphere-sentinel 知识点提取

> 源码：`/data/workspace/java-training-camp/cloud-native-code/share/microsphere-alibaba-sentinel`（依赖链第 14 站；30 生产文件——与 microsphere-sentinel 内容一致；另 30 文件双份）
> 提取时间：2026-08-13（批 1：commons 10 + 适配器 12 + autoconfigure 4 + condition 2 + jmx/cloud）
> 状态：已提取（骨架归组式）；MCP 索引已建（1222 节点/2963 边）
> 关联：**13 druid（SentinelAlibabaDruidFilter extends AbstractStatementFilter——限流贴到刚提取的 Filter 模板）/ 12 mybatis（extends ExecutorFilter）/ 10 redis（RedisConnectionInterceptor）/ 11 observability（P6Spy）**——跨仓库能力组合实证
> 历史交叉验证：`microsphere-analysis/07-microsphere-sentinel-analysis/`（1331 行 8 部分 + REQ——模板/插件 SPI/5 扩展点/6 适配器/官方对照）

## 一、仓库定位

**Sentinel 的 Spring Boot 集成层——不是重写 Sentinel**——把官方流控能力"贴"到 6 个基础设施框架原生扩展点（Druid Filter/MyBatis ExecutorFilter/Hibernate EntityCallback/P6Spy JdbcEventListener/Spring RedisConnectionInterceptor/Web HandlerMethodInterceptor）——**5 类适配器官方没有**（官方 sentinel-adapter 只有 webmvc/webflux/gateway——历史 REQ 表实证）。核心维度：[分布式问题]（限流落地）+ [工程问题]（框架扩展点组合）。

**与 06-nacos 本质区别**（历史 REQ）：06 重写官方 SDK（thin HTTP wrapper——被废弃）；07 是**官方 SDK 能力扩展到官方没覆盖的领域**——高价值定位。

## 前置条件清单

读者需先掌握：1. Sentinel 核心（SphU.entry/Entry/ContextUtil/Tracer——官方 API）2. 12/13/10 仓库的框架扩展点（ExecutorFilter/AbstractStatementFilter/RedisConnectionInterceptor）3. 02 仓库 SPI 体系
未达前置者，先补：12/13/10 仓库 outline + Sentinel 官方源码（本地 code/spring/sentinel 907 文件）

## 掌握度

目标读者：中级偏上（读源码多、Spring 熟——HANDOVER 画像）
讲解策略：模板 API（免样板）+ 插件 SPI + 5 扩展点架构资产（历史 8 部分主线）

---

## 二、逐文件映射 + 原子记录（骨架归组式）

### 包: `io.microsphere.alibaba.sentinel.common`（批 1a：10 文件）

#### KP-1301 `SentinelTemplate` 模板 API + `SentinelPlugin` 插件 SPI（SentinelTemplate.java:42-64 + SentinelOperations.java:64-74 + SentinelPlugin.java:37-104 + AbstractSentinelPlugin.java:33-104 + SimpleSentinelPlugin + SentinelContext.java:38-110 + SentinelPluginRepository.java:34 + SimpleSentinelPluginRepository:35-72 + **JMXSentinelPluginRepository:45-81** + SentinelUtils:42-106 + SentinelConstants——11 文件全列）

- **维度**：[工程问题]（模板/SPI）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：Sentinel 官方 API（SphU.entry/ContextUtil/Tracer）、MethodHandle（02 仓库）、JMX MBean
- **需求**：**免样板流控模板 + 可插拔插件**——SentinelTemplate 包装 entry 生命周期（免 try/catch/finally 样板——历史第一部分）；SentinelPlugin SPI 统一流控资源声明（name/contextName/origin/resourceType/trafficType）
- **参考实现**：**模板 API**（SentinelTemplate :42——implements SentinelOperations（:64——**execute 重载族**（Runnable/Consumer/Function :74——**免样板**）+ **begin()**（:64——entry 生命周期开启——**手控模式**（begin/end 显式））；**操作接口**（SentinelOperations :64——execute 家族契约）；**插件 SPI**（SentinelPlugin :37——资源声明五元组（:104——getTrafficType）+ enable/disable（:77-84——**插件开关**）；**插件基座**（AbstractSentinelPlugin :33——五元组字段（:43-61——构造注入）+ 开关实现）；**插件仓库**（SentinelPluginRepository :34——install/get/uninstall——**Simple 内存版**（:35-72）+ **JMXSentinelPluginRepository**（:45-81——**JMX MBean 管理**（JMX_DOMAIN "io.microsphere.sentinel" :50 + OBJECT_NAME_PATTERN :55——**运行期 JMX 管理插件**（历史第二部分——插件 SPI + JMX 管理））；**上下文**（SentinelContext :38——Entry 载体（:52——request 上下文）；**工具**（SentinelUtils :42——**MethodHandle resetContextMap**（:51——ContextUtil.resetContextMap 反射（02 仓库 MethodHandle 模式）+ shutdown hook 清理（:57——JVM 退出重置上下文）+ buildResourceName（:79——方法 → 资源名）
- **对比取舍**：**知识增量**：①**模板 API 免样板**（:74——execute 重载族——**Sentinel 官方无模板层**（官方要求手写 entry/try/finally——微球包装）；②**插件五元组**（:37——**流控资源的统一声明模型**（后续所有适配器复用——架构资产）；③**JMX 管理插件**（:45——**运行期管理通道**（MBean 开关插件——不重启改插件状态）；④**MethodHandle 官方内部 API**（:51——ContextUtil.resetContextMap（与 02 仓库 MethodHandle 版本探测同族）
- **测试佐证**：sentinel 测试族（[补扫]）
- **my-xhs**：**已用（实证）**——my-xhs 用官方 sentinel（sentinel-datasource-nacos pom 实证——07 对照）——**官方 API 手写 entry**（无模板层——微球模板可借鉴——my-xhs 若有多个流控点）

#### KP-1302 六适配器族（统一模式：AbstractSentinelPlugin + 框架扩展点双实现）（SentinelMyBatisExecutorFilter:48-56 + SentinelAlibabaDruidFilter:46-50 + SentinelRedisCommandInterceptor:50-65 + SentinelJdbcEventListener:49-57 + SentinelHandlerMethodInterceptor:59-64 + SentinelHibernateEntityCallback + Constants 6 全列：SentinelAlibabaDruidConstants/SentinelMyBatisConstants/SentinelRedisConstants/SentinelP6SpyConstants/SentinelSpringWebConstants/SentinelHibernateConstants——12 文件全列）

- **维度**：[分布式问题]（限流落地）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：12/13/10 仓库扩展点（ExecutorFilter/AbstractStatementFilter/RedisConnectionInterceptor）+ P6Spy JdbcEventListener + HandlerMethodInterceptor + Hibernate EntityCallback
- **需求**：**把流控贴到框架原生扩展点**——6 个适配器（MyBatis/Druid/Redis/P6Spy/Web/Hibernate——**5 类官方没有**）
- **参考实现**：**统一架构模式**（每个适配器 = **extends AbstractSentinelPlugin implements {框架扩展点}**——SentinelMyBatisExecutorFilter :48（`extends AbstractSentinelPlugin implements ExecutorFilter`——**插件基座 + 框架扩展点双实现**——流控声明来自插件、拦截来自框架）+ **SentinelOperations 注入**（:50——**模板执行**（拦截点 → template.execute——统一限流入口））；**适配器代表**（MyBatis :48——ExecutorFilter 挂载（12 仓库扩展点）/ Druid :46——`extends AbstractStatementFilter implements SentinelPlugin`（13 仓库 Filter 模板——**delegate 模式**（:48——SentinelPlugin delegate——组合而非继承插件））/ Redis :50——RedisConnectionInterceptor（10 仓库）/ P6Spy :49——SimpleJdbcEventListener（11 仓库）/ Web :59——HandlerMethodInterceptor + **BEAN_NAME 固定**（:62）；**常量族**（各自 Constants 6——DEFAULT_CONTEXT_NAME（:44——`microsphere_sentinel_mybatis_context` 等——**上下文命名约定**）+ enabled 属性（:52——`microsphere.sentinel.mybatis.enabled`）
- **对比取舍**：**知识增量**：①**"插件 + 扩展点"双实现统一模式**（:48——**跨框架限流的架构统一**（6 个适配器同一模式——插件声明 + 扩展点挂载 + 模板执行）；②**与已提取仓库的组合实证**（12/13/10/11 扩展点是本仓库的挂载点——**跨仓库能力组合**（提取时分别学的扩展点在此汇合）；③**delegate vs 继承**（Druid 适配器组合 SentinelPlugin :48 vs MyBatis 适配器继承 AbstractSentinelPlugin :48——**两种插件接入风格**（Druid 因已继承 FilterAdapter 只能组合）
- **my-xhs**：**该用没用（实证）**——my-xhs 用官方 sentinel webmvc 适配器（官方覆盖 Web 层）——**数据库/Redis 级限流为差距**（若 my-xhs 需要 SQL/命令级限流——官方没有）

#### KP-1303 自动配置族（SentinelAlibabaDruidAutoConfiguration:40-53 + SentinelMyBatisAutoConfiguration:44-60 + SentinelRedisAutoConfiguration:24-37 + SentinelSpringWebAutoConfiguration:29-42 + ConditionalOnSentinelAvailable + ConditionalOnSentinelEnabled + SentinelCloudAutoConfiguration——7 文件全列）

- **维度**：[工程问题]（自动配置）| **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：条件组合注解（04 仓库）、@AutoConfigureAfter
- **需求**：**加依赖即生效**——各适配器自动配置（SPI 自动注册——历史 REQ 定位）
- **参考实现**：**条件组合**（MyBatis 版 :44-47——@ConditionalOnSentinelAvailable + **@ConditionalOnMyBatisAvailable**（:45——**框架可用条件**（12 仓库条件复用）+ @ConditionalOnProperty(enabled, matchIfMissing=true) :46 + @ConditionalOnClass :47——**四重条件**（Sentinel 可用 + 框架可用 + 开关 + 类存在）；**装配顺序**（@AutoConfigureAfter :51——官方 + 微球框架配置之后）；**Web 版**（:29——@ConditionalOnWebApplication(type=ANY) :29——**MVC/WebFlux 双栈**
- **对比取舍**：**知识增量**：①**跨仓库条件组合**（:45——@ConditionalOnMyBatisAvailable（12 仓库条件注解被 14 复用——**条件体系跨仓库消费**）；②**四重条件装配**（:44-47——**集成装配的标准条件链**
- **my-xhs**：**该用没用（实证）**——官方 sentinel spring-cloud-alibaba 自动配置覆盖 Web；数据库/Redis 适配器自动配置无（官方无）

### 包总结（sentinel 批 1）

- **核心命题**：**"模板 API + 插件 SPI + 6 适配器统一模式"**——SentinelTemplate（免样板）+ SentinelPlugin（五元组声明）+ 适配器（插件+扩展点双实现）+ 四重条件装配——**5 类独有适配器**（官方无）
- **跨仓库组合实证**：12/13/10/11 扩展点 = 本仓库挂载点——**提取时的分散知识点在此汇合**
- **历史材料**：07 分析 8 部分主线全映射（模板/插件+JMX/5 扩展点/6 适配器/官方对照）

---

## 三、深度 review 七项报告（批 1 合并）

> 2026-08-13 批判性 review：穷尽性核对先行——30/30 生产文件全覆盖（骨架归组式）。

- [x] **① 源码行号精确核对**：KP-1301:42/:64/:74/:64/:37/:104/:77-84/:34/:35-72/:45-81/:38/:51/:79、KP-1302:48/:50/:46-50/:62、KP-1303:44-47/:45/:29——全部 grep 实证 ✓
- [x] **② 穷尽性**：30/30 生产文件全覆盖（basename 脚本核对——铁律 #7）✓
- [x] **③ 空节标注**：Hibernate 适配器细节未深读（归组）✓
- [x] **④ 过时三级**：3 KP 全部标注（均时间无关模式）✓
- [x] **⑤ 重复内容**：扩展点与 12/13/10/11 仓库（跨仓库引用）；JMX 与 02 仓库管理域 ✓
- [x] **⑤b 引用目标核对**：Sentinel 官方（SphU/Entry/ContextUtil——本地 code/spring/sentinel 907 文件有）✓；框架扩展点（12/13/10 本地源码有）✓
- [x] **⑥ 诚实标注**：Hibernate 适配器归组 Medium ✓
- [x] **⑦ 命名空间迁移**：N/A ✓

### 历史 REQ 交叉验证（14-sentinel）

| # | 历史断言 | 验证 | 落点 |
|---|---------|------|------|
| 定位-非重写 | 不是重写 Sentinel——官方 SDK 能力扩展 | ✅ 证实（SentinelOperations/SentinelPlugin 包装官方 API——无重写） | KP-1301 |
| 5 类独有 | MyBatis/Druid/Hibernate/P6Spy/Redis 适配器官方无 | ✅ 证实（六适配器族——官方只有 webmvc/webflux/gateway） | KP-1302 |
| 模板 API | 免 try/catch 样板 | ✅ 证实（execute 重载族 :74） | KP-1301 |
| 插件 SPI + JMX | 插件五元组 + JMX MBean 管理 | ✅ 证实（:37 + JMXSentinelPluginRepository :45） | KP-1301 |
| 与 06 区别 | 06 重写 SDK（废弃）vs 07 扩展 | ✅ 证实（本仓库无重写——包装官方 API） | 定位 |

**验证成果**：5/5 全部证实——无证伪项

---

## 四、my-xhs 落地判定汇总表（2026-08-13 实证版）

| KP | 判定 | 说明 |
|----|------|------|
| KP-1301 | 已用（部分） | my-xhs 官方 sentinel（sentinel-datasource-nacos 实证）——**模板 API 可借鉴**（多个流控点免样板） |
| KP-1302 | 该用没用 | **数据库/Redis 级限流为差距**（官方没有——若 my-xhs 需要 SQL/命令级限流） |
| KP-1303 | 该用没用 | 官方 webmvc 自动配置覆盖 Web；数据库/Redis 适配器无 |

**汇总**：已用 1 / 该用没用 2。
**核心结论**：my-xhs 用官方 sentinel（Web 层覆盖）——**SQL/Redis 命令级限流是官方空白**（microsphere 5 类独有适配器的落地价值点）；模板 API 免样板可借鉴。

### 深度 review 补充（问题域对照轮——历史 07 分析正文 8 部分）

#### KP-1304 问题域补充（三种交互模式 + 高基数风险 + ThreadLocal 风险）

- **维度**：[分布式问题]（限流落地细节）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：SentinelContext 传递、ThreadLocal、框架属性（exchange/context）
- **需求**：**适配器的上下文传递与资源命名问题域**——历史 07 分析 :1025-1060/:1160-1175
- **参考实现**：**三种交互模式（历史 07 分析——适配器分类）**：**模式一——直接 begin/end 同步包装**（MyBatis ExecutorFilter——doInSentinel 包装（SentinelMyBatisExecutorFilter :63/:68/:73——**同步链内直接包裹**——无上下文传递问题）；**模式二——pre/post + ThreadLocal**（分离开的钩子——ThreadLocal 传递 SentinelContext——**风险：异步执行时 ThreadLocal 丢失**（历史 :1032——**与 08-redis DynamicRedisConnectionFactory 同问题——跨仓库同族**（线程池场景上下文丢失））；**模式三——双阶段 + 框架属性**（Redis/Spring Web——beforeExecute 存框架属性（setSentinelContext）+ afterExecute 从属性取（:1040-1046——**不依赖 ThreadLocal——异步安全**）；**资源命名策略（高基数风险——历史 :1160-1175）**：P6Spy 用**裸 SQL 字符串**（SentinelJdbcEventListener :93——`statementInformation.getSql()`——**每条 SQL 一个资源**（Dashboard 可见具体 SQL——优点）vs **高基数风险**（动态拼接 SQL 资源爆炸）——**缓解**：isEligibleStatement 只允许 PreparedStatementInformation（:105——`?` 占位符 SQL 基数可控——但同 SQL 多 Mapper 共享资源无法区分来源）；**命名粒度对照**（MyBatis Mapper 方法级 `com.example.mapper.UserMapper.selectUser` vs P6Spy SQL 文本级 `SELECT ... WHERE id = ?`——历史 :1170 表）
- **对比取舍**：**知识增量**：①**三种上下文传递模式**（直接/ThreadLocal/框架属性——**适配器的上下文传递选型**（同步链直接包装/异步场景用框架属性——ThreadLocal 的异步缺陷）；②**高基数资源命名**（:93——**裸 SQL 资源名的取舍**（精确 vs 基数爆炸——PreparedStatement 过滤缓解——**资源命名策略的基数控制**）；③**ThreadLocal 异步丢失家族**（本仓库 + 08-redis——**跨仓库同族问题**（线程池/虚拟线程场景）
- **my-xhs**：**该用没用（实证）**——my-xhs 官方 sentinel webmvc 适配器（无 SQL 级限流——无高基数问题）；**资源命名基数控制直接适用**（若 my-xhs 自写限流资源命名）

### 包总结（问题域补充）

- **核心命题**：**"上下文传递三模式 + 资源命名基数"**——历史 07 分析正文 8 部分全映射
- **验证成果**：P6Spy 裸 SQL（:93）/:105 PreparedStatement 过滤/MyBatis 模式一（:63）全部源码实证
- **跨仓库同族**：ThreadLocal 异步丢失（+08-redis）——第三种跨仓库同族问题
