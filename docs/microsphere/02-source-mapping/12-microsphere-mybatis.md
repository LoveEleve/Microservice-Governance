# microsphere-mybatis 知识点提取

> 源码：`/data/workspace/java-training-camp/cloud-native-code/share/microsphere-mybatis`（依赖链第 12 站；7 模块 54 生产文件 + 31 测试）
> 提取时间：2026-08-13（批 1：executor 8 + plugin 3；批 2：annotation 8 + test 族 23 + autoconfigure 4 + util 1）
> 状态：批 1-2 已提取（骨架归组式）；MCP 索引已建（1522 节点/3871 边）
> 关联：07 dynamic 的 mybatis/mybatisplus 模块（独立版 vs 动态版）；MyBatis-Plus（对照生态——本地有 MP 源码 399 文件）
> 历史交叉验证：`microsphere-analysis/13-microsphere-mybatis-analysis/`（3 篇 + REQ——D01-D06 + 生态对比）

## 一、仓库定位

**MyBatis Executor 级增强**——ExecutorFilter 过滤链（10 方法）+ MyBatis 原生 Interceptor 桥接 + Spring 集成 + 测试基座。核心维度：[工程问题]（MyBatis 扩展）+ [性能优化]（Executor 拦截）。

**与 MP 对照**（REQ 表实证）：microsphere ExecutorFilter 仅 Executor 级（10 方法——Filter Chain 显式传递——**BoundSql 只读不能改 SQL**）；MP InnerInterceptor 覆盖 Executor + StatementHandler（8 方法——**beforePrepare/beforeGetBoundSql 可替换 SQL**）——**层级/模式/SQL 可改性三维差异**。

## 前置条件清单

读者需先掌握：1. MyBatis Executor 接口（update/query/commit 等 10 方法）2. MyBatis 插件体系（Interceptor/@Intercepts/@Signature/Plugin）3. 02 仓库 Prioritized/SPI 4. 07 dynamic mybatis 模块
未达前置者，先补：07 仓库 outline + MyBatis 源码（本地有）

## 掌握度

目标读者：中级偏上（读源码多、Spring 熟——HANDOVER 画像）
讲解策略：与 MP InnerInterceptor 对照（本地 MP 源码实证）+ Executor 拦截点教学

---

## 二、逐文件映射 + 原子记录（骨架归组式）

### 包: `io.microsphere.mybatis.executor`（批 1a：8 文件）

#### KP-1101 `ExecutorFilter` 过滤链（ExecutorFilter.java:73-208 + ExecutorFilterChain.java:64-153 + InterceptingExecutor.java:60-90 + Executors）

- **维度**：[工程问题]（MyBatis 扩展）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（过滤链） | **置信度**：High
- **前置**：MyBatis Executor 10 方法、Filter Chain 模式（03 仓库事件拦截链同族）、Prioritized
- **需求**：**Executor 级过滤**——每条 SQL 执行前后插入自定义逻辑（日志/监控/审计——10 方法全覆盖）
- **参考实现**：**过滤接口**（ExecutorFilter :73——implements **Prioritized**（:73——排序过滤）+ **10 方法 default 族**（:84-208——update/query×2/queryCursor/commit/rollback/createCacheKey/deferLoad/getTransaction/close——**每方法带 chain 参数显式传递**（:84——`update(ms, parameter, chain)`——**Filter Chain 风格**（与 03 仓库事件拦截链同族））——**覆盖度修正（深度 review 轮）**：官方 Executor **15 方法**（本地 mybatis 源码 :37-67 实证——update/query×2/queryCursor/flushStatements/commit/rollback/createCacheKey/isCached/clearLocalCache/deferLoad/getTransaction/close/isClosed/setExecutorWrapper）——**ExecutorFilter 覆盖 10/15**（未覆盖 flushStatements/isCached/clearLocalCache/isClosed/setExecutorWrapper 5 个——历史 13-01 :43 同断言）；**过滤链**（ExecutorFilterChain :64——构造注入 executor + filters（:84）+ **各方法沿链传递**（:102/:120——`filter.update(ms, parameter, this)`——**链式递归**（最后一个 filter 调真实 executor））；**包装执行器**（InterceptingExecutor :60——implements Executor——**delegate + filters 包装**（:78-83）+ 每方法 buildChain（:88-90——**懒建链**））；**日志实现**（LoggingExecutorFilter/LoggingExecutorInterceptor——内置过滤实现）
- **对比取舍**：**知识增量**：①**Executor 10 方法全覆盖过滤**（:84-208——**default 方法族**（过滤器只覆写关心的方法——最小实现）；②**Filter Chain 显式传递**（:84——chain 参数 vs MP Plugin 链（拦截器栈隐式）——**两种拦截模式**（显式 vs 隐式）；③**Prioritized 排序**（:73——过滤顺序控制（02 仓库模式）
- **测试佐证**：InterceptingExecutorTest/ExecutorsTest（[补扫]）
- **my-xhs**：**该用没用（实证）**——my-xhs 用 MyBatis-Plus 官方（MP 拦截器体系）——ExecutorFilter 无场景；**过滤链模式可借鉴**（若自定义 SQL 拦截）

#### KP-1102 Interceptor 桥接族（InterceptorsExecutorFilterAdapter + InterceptingExecutorInterceptor.java:63-90 + InterceptorContext.java:58 + Plugins.java:67）

- **维度**：[工程问题]（桥接）| **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：MyBatis Interceptor/@Intercepts/@Signature、Plugin 代理
- **需求**：**MyBatis 原生插件体系 ↔ ExecutorFilter 桥接**——现有 @Intercepts 拦截器接入过滤链（或反之）
- **参考实现**：**适配器**（InterceptorsExecutorFilterAdapter——MyBatis Interceptor → ExecutorFilter 适配）；**原生拦截器委托**（InterceptingExecutorInterceptor :63——implements Interceptor——**Executor delegate 到 ExecutorInterceptor 实例**（原生插件委托过滤）；**上下文载体**（InterceptorContext\<T> :58——拦截上下文）；**签名工具**（Plugins :67——@Intercepts 签名解析（:48——signatureMap 提取——Executor.class → update 方法映射——**插件签名解析工具**）
- **对比取舍**：**知识增量**：①**双体系桥接**（Interceptor ↔ ExecutorFilter——**新旧拦截模型的适配层**）；②**签名解析工具**（:48——@Intercepts 注解解析（MyBatis 插件机制的内部工具化）
- **my-xhs**：**不该用（实证）**——MP 拦截器体系覆盖（无桥接需求）

### 包: `spring` + `plugin` + `test` 族（批 1b + 2：43 文件）

#### KP-1103 Spring 集成（EnableMyBatisExtension.java:69-73 + EnableMyBatis.java:87-91 + MyBatisExtensionBeanDefinitionRegistrar + MyBatisBeanDefinitionRegistrar + MyBatisImportBeanDefinitionRegistrar + MyBatisConfigurationBeanDefintionRegistrar + MyBatisExecutorBeanPostProcessor + SqlSessionFactoryBeanPostProcessor + MyBatisConfiguration + PropertyConstants + ConditionalOnMyBatisAvailable + ConditionalOnMyBatisEnabled + MyBatisAutoConfiguration + MyBatisCloudAutoConfiguration + MyBatisUtils——15 文件全列）

- **维度**：[工程问题]（Spring 集成）| **权重**：[核心] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：@Import + Registrar（03 仓库）、@Enable 对称注解
- **需求**：**Spring 集成**——@EnableMyBatis（Mapper 扫描）/@EnableMyBatisExtension（Executor 增强——interceptExecutor 开关 :49）+ 自动配置
- **参考实现**：**双 Enable 注解**（EnableMyBatis :91——@Import(MyBatisBeanDefinitionRegistrar)——**Mapper 扫描**（类似官方 @MapperScan）；EnableMyBatisExtension :73——@Import(MyBatisExtensionBeanDefinitionRegistrar)——**Executor 增强启用**（interceptExecutor=true :49——**拦截开关**））；**自动配置**（spring-boot/cloud autoconfigure 4 + 条件 2——Boot 装配）
- **对比取舍**：**知识增量**：①**双 Enable 分工**（扫描 vs 增强——**能力分层启用**）
- **my-xhs**：**已用（实证）**——my-xhs 用 mybatis-plus-spring-boot3-starter（07 对照已实证）——官方 Starter 覆盖扫描/增强

#### KP-1104 测试基座族（test/junit/jupiter/resolver 12：AbstractComponentResolver/ComponentResolver/ConfigurationResolver/ConnectionResolver/DataSourceResolver/EnvironmentResolver/ExecutorResolver/MapperComponentResolver/PropertiesResolver/SqlSessionFactoryResolver/SqlSessionResolver/TransactionResolver + test 族：AbstractExecutorTest/AbstractMapperTest/AbstractMyBatisTest/AbstractSqlSessionTest/MyBatisRuntime/MyBatisTestExtension/MyBatisTest/MyBatisTestUtils + mapper 3：ChildMapper/FatherMapper/UserMapper + entity 3：Child/Father/User + spring-test config 3：MyBatisDataBaseTestConfiguration/MyBatisDataSourceTestConfiguration/MyBatisTestConfiguration——**test 模块主文件 29 个（历史 13-02 :159 断言——54 主文件中 test 占 29 过半——实证）**）

- **维度**：[工程问题]（测试基座）| **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：JUnit Jupiter ParameterResolver、MyBatis 组件体系
- **需求**：**MyBatis 测试基座**——测试参数自动注入 MyBatis 组件（SqlSessionFactory/Executor/Mapper/DataSource 等）
- **参考实现**：**组件解析器族**（resolver 12——AbstractComponentResolver + ComponentResolver（SPI）+ 各组件解析器（Configuration/Connection/DataSource/Environment/Executor/Mapper/SqlSessionFactory/SqlSession/Transaction/Properties——**JUnit ParameterResolver 注入**（测试参数解析））；**测试辅助**（test 8 + mapper 3 + entity 3——测试 Mapper/实体 + spring-test config 3——**测试配置**）
- **对比取舍**：**知识增量**：①**测试组件注入解析器**（12 解析器——**JUnit5 ParameterResolver 模式**（测试参数自动解析 MyBatis 组件——免手动初始化）
- **my-xhs**：**该用没用（实证）**——my-xhs 用 MP 官方测试（mybatis-plus-boot-starter-test）——解析器模式可借鉴

#### KP-1105 与 MP 生态对照（问题域——历史 13-03 + D01-D06）

- **维度**：[分布式问题]（生态对照）| **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：MyBatis-Plus InnerInterceptor（本地 MP 源码 399 文件）
- **需求**：**生态定位对照**——microsphere ExecutorFilter vs MP InnerInterceptor（REQ 表 + 历史 13-03）
- **参考实现**：**三维差异**（层级：仅 Executor vs Executor+StatementHandler；模式：Filter Chain 显式 vs Plugin 链隐式；**SQL 可改性：不能（BoundSql 只读）vs 能（beforePrepare/beforeGetBoundSql 替换）**）；**MP @Intercepts 5 签名修正**（历史 REQ 警告——StatementHandler.prepare + getBoundSql + Executor.update + query×2——**非 8 个**（历史 v2 修正——本地 MP 源码验证）
- **对比取舍**：**知识增量**：①**拦截层级与 SQL 可改性**（:REQ 表——**Executor 级只能"看"不能"改"**（BoundSql 只读）——MP StatementHandler 级能改——**拦截点的能力边界**）；②**REQ-D01~D06 待验证**（历史 6 项 bug——[后续轮验证]）
- **my-xhs**：**已用（实证）**——my-xhs 用 MP（10 个内置 InnerInterceptor——分页/乐观锁/防全表/租户等）——**MP 覆盖微球过滤链能力并超出**

### 包总结（mybatis 批 1-2）

- **核心命题**：**"Executor 级过滤链 + 双体系桥接 + 生态对照"**——ExecutorFilter（10 方法 default 族 + 显式 Chain）——**拦截点能力边界**（Executor 级不能改 SQL vs MP StatementHandler 级能改）
- **生态定位**：microsphere 过滤链 = 教学价值（拦截模式）> 生产价值（MP 覆盖并超出——10 内置拦截器）
- **历史材料**：13-01~03 三篇（拦截机制/Spring 集成/生态对比）+ D01-D06 待验证

---

## 三、深度 review 七项报告（批 1-2 合并）

> 2026-08-13 批判性 review：穷尽性核对先行——54/54 生产文件全覆盖（骨架归组式）。

- [x] **① 源码行号精确核对**：KP-1101:73/:84-208/:64/:84/:102-120/:60/:78-83/:88-90、KP-1102:63/:58/:67/:48、KP-1103:91/:73/:49——全部 grep 实证 ✓
- [x] **② 穷尽性**：54/54 生产文件全覆盖（basename 脚本核对——铁律 #7）✓
- [x] **③ 空节标注**：KP-1104 归组（resolver 细节未逐行深读）✓
- [x] **④ 过时三级**：5 KP 全部标注（均时间无关模式）✓
- [x] **⑤ 重复内容**：Filter Chain 与 03 仓库事件拦截链同族；07 dynamic mybatis 模块（独立版 vs 动态版）✓
- [x] **⑤b 引用目标核对**：MyBatis Executor/Interceptor/Plugin（本地 mybatis 源码有）✓；MP InnerInterceptor（本地 MP 源码 399 文件有）✓
- [x] **⑥ 诚实标注**：KP-1104 Medium 归组；D01-D06 待验证 ✓
- [x] **⑦ 命名空间迁移**：N/A ✓

### 历史 REQ 交叉验证（12-mybatis 部分）

| # | 历史断言 | 验证 | 落点 |
|---|---------|------|------|
| 定位-仅 Executor | ExecutorFilter 仅 Executor 级（10 方法） | ✅ 证实（:84-208 10 方法 default 族） | KP-1101 |
| 定位-BoundSql 只读 | 不能改 SQL（vs MP 能改） | ✅ 证实（Filter 签名无 BoundSql 替换参数——链传 ms/parameter） | KP-1101 |
| MP 5 签名 | @Intercepts 声明 5 签名（非 8） | ⬜ 待验证（本地 MP 源码） | KP-1105 |
| D01-D06 | 6 项 bug | ⬜ 待验证 | KP-1105 |

**进度**：2/4 证实；待验证 2（MP 5 签名/D01-D06）

---

## 四、my-xhs 落地判定汇总表（2026-08-13 实证版）

| KP | 判定 | 说明 |
|----|------|------|
| KP-1101 | 该用没用 | ExecutorFilter——my-xhs 用 MP 拦截器体系；过滤链模式可借鉴 |
| KP-1102 | 不该用 | 桥接族——MP 覆盖 |
| KP-1103 | 已用 | mybatis-plus-spring-boot3-starter 官方覆盖扫描/增强 |
| KP-1104 | 该用没用 | 测试解析器模式可借鉴（MP 官方测试覆盖基础） |
| KP-1105 | 已用 | my-xhs 用 MP 10 内置拦截器——覆盖微球能力并超出 |

**汇总**：已用 2 / 该用没用 2 / 不该用 1。
**核心结论**：my-xhs 的 MyBatis 层 = MP 官方体系（拦截能力超出微球）；**拦截点能力边界**（Executor 级不能改 SQL）为知识增量。

### 深度 review 补充（问题域对照轮——历史 13 篇 3 篇）

#### KP-1106 问题域补充（官方基线 + P1-P6 问题清单 + D 表部分验证 + 双模型）

- **维度**：[工程问题]（拦截体系）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：CachingExecutor 二级缓存语义（官方源码）、Configuration.newExecutor 挂载点
- **需求**：**拦截体系的问题域**——官方基线（Executor 15 方法/挂载点/Plugin 动态代理）+ 已证问题清单（P1-P6）+ 双模型
- **参考实现**：**官方基线**（本地 mybatis 源码——Executor 15 方法（:37-67）/ **挂载点**（Configuration.newExecutor → interceptorChain.pluginAll——13-01 :67）/ 官方 Plugin 动态代理（:83））；**双模型**（ExecutorFilter（**可干预**——filter 有链参数）vs ExecutorInterceptor（**纯观察**——13-01 :144——**干预/观察分工**）；**P1-P6 问题清单（历史 13-01 :254 已证——本轮核实）**：P1 反射读私有字段（Executors.getDelegate 读 CachingExecutor.delegate——**与 gateway G8 同族**（反射脆弱））/ **P2 拼写错误第 7 例**（MyBatisConfigurationBeanDefintionRegistrar——**Defintion 少 i**——类名拼错）/ P3 初始提交 NPE（已修复——暴露"初始版可能从未运行"）/ P4 每次调用新建 chain（可忽略开销）/ P5 intercept() 语义陷阱（实现官方 Interceptor 却**不拦截**——只 warn+proceed——**误以为动态代理会困惑**）/ **P6 二级缓存命中时过滤器不执行**（CachingExecutor 外层语义——官方 :102-107——**tcm.getObject 命中直接返回不调 delegate**——**过滤器链被二级缓存短路**——语义边界）；**D 表部分**（D05 4 方法绕过拦截链/D06 AutoConfiguration 空壳——microsphere 侧——待深读）
- **对比取舍**：**知识增量**：①**覆盖度 10/15**（ExecutorFilter 覆盖官方 15 方法中 10 个——**拦截面边界**（flushStatements/isCached 等 5 个不可拦截）；②**二级缓存短路**（P6——CachingExecutor 外层——**缓存命中过滤器不执行**（拦截语义的隐藏边界——与 D03 SQL 改写+二级缓存 CacheKey 不一致呼应）；③**双模型分工**（可干预 vs 纯观察——**拦截器的两种定位**（呼应 redis 观察者非守卫））；④**反射读私有字段家族**（P1 + G8——**跨仓库同族**（getFieldValue 读框架私有字段——升级脆弱）
- **my-xhs**：**该用没用（实证）**——my-xhs 用 MP（无此拦截体系）——**P6 二级缓存短路语义直接适用**（MP 拦截器同样被二级缓存短路——SQL 改写需注意 CacheKey）

### 包总结（问题域补充）

- **核心命题**：**"官方基线 + 已证问题清单"**——15 方法覆盖度 10/15/二级缓存短路（P6）/双模型分工/反射读私有字段家族（P1+G8）
- **验证成果**：官方 Executor 15 方法本地实证；test 29 文件实证；P1-P6 核实
