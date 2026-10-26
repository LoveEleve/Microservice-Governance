# Microsphere MyBatis Executor 增强知识大纲（mybatis 触发面）

> 来源：`mapping/12-microsphere-mybatis.md` 5 个 KP 的知识本体提炼
> 性质：**源码侧 outline（L1.5）**——mapping 是文件映射（过程），本大纲是知识本体（结果）
> 组织：按知识维度，非按文件；每个知识点标注来源 KP + 六元元数据 + my-xhs 落地实证
> 用途：①自学/面试知识图谱（MyBatis 拦截体系）②与课程 L1 合并成 L3 的源码侧素材 ③my-xhs 对照
> 覆盖核对：5/5 KP 全部归属（文末核对表）

---

## 一、Executor 级过滤链（核心命题）[工程问题]

> **核心命题**：ExecutorFilter 过滤 Executor 全部 10 方法——**Filter Chain 显式传递** vs MP Plugin 链隐式——**拦截点能力边界**（Executor 级 BoundSql 只读不能改 SQL）。

### 1.1 ExecutorFilter + Chain [🔴 P1] [时间无关模式]
- **来源**：KP-1101
- **机制**：**10 方法 default 族**（:84-208——update/query×2/queryCursor/commit/rollback/createCacheKey/deferLoad/getTransaction/close——过滤器只覆写关心方法）+ **chain 参数显式传递**（:84——Filter Chain 风格）+ **Prioritized 排序**（:73）+ **InterceptingExecutor 包装**（:60——delegate + 懒建链 :88-90）
- **对比**：两种拦截模式（显式 Chain vs 隐式 Plugin 栈）
- **my-xhs**：该用没用——MP 拦截器体系覆盖；过滤链模式可借鉴

### 1.2 Interceptor 桥接 [🟡 P2] [时间无关模式]
- **来源**：KP-1102
- **机制**：**双体系适配**（InterceptorsExecutorFilterAdapter——MyBatis Interceptor ↔ ExecutorFilter）+ InterceptingExecutorInterceptor 委托 + **@Intercepts 签名解析**（Plugins:48——signatureMap 工具）
- **my-xhs**：不该用——MP 覆盖

---

## 二、Spring 集成与测试基座 [工程问题]

### 2.1 双 Enable 注解 [🟡 P2] [时间无关模式]
- **来源**：KP-1103
- **机制**：@EnableMyBatis（Mapper 扫描 :91）+ @EnableMyBatisExtension（Executor 增强——interceptExecutor 开关 :49）——**能力分层启用**
- **my-xhs**：已用——MP Starter 官方覆盖

### 2.2 测试解析器族 [🟡 P2] [时间无关模式]
- **来源**：KP-1104
- **机制**：**JUnit5 ParameterResolver 组件注入**（resolver 12——SqlSessionFactory/Executor/Mapper/DataSource/Transaction 等——测试参数自动解析）
- **my-xhs**：该用没用——解析器模式可借鉴

---

## 三、生态对照 [分布式问题]

### 3.1 microsphere vs MyBatis-Plus [🟡 P2] [时间无关模式]
- **来源**：KP-1105
- **机制**：**三维差异**（层级：Executor vs Executor+StatementHandler；模式：显式 Chain vs 隐式 Plugin；**SQL 可改性：不能 vs 能**（BoundSql 只读 vs beforePrepare 替换））；MP @Intercepts 5 签名修正（待本地 MP 源码验证）
- **my-xhs**：已用——MP 10 内置拦截器覆盖微球能力并超出

---

## 覆盖核对（5/5 + 对照）

| 维度 | 覆盖 KP | 数 |
|------|---------|:--:|
| 一、Executor 过滤链 | 1101,1102 | 2 |
| 二、Spring 与测试 | 1103,1104 | 2 |
| 三、生态对照 | 1105 | 1 |

**去重后唯一 KP**：1101-1105 全部 = **5/5 ✓**
**无孤儿 KP** ✓
**历史进度**：2/4 证实（Executor 级定位/BoundSql 只读）；待验证 2（MP 5 签名/D01-D06）
**my-xhs 对照**：已用 2 / 该用没用 2 / 不该用 1——MP 覆盖微球拦截能力；**拦截点能力边界**为知识增量。

---

## 四、问题域知识（历史 13 篇 3 篇对照）[工程问题]

### 4.1 官方基线 + 覆盖度 [🔴 P1] [时间无关模式]
- **来源**：KP-1106（历史 13-01）
- **机制**：**官方 Executor 15 方法**（本地 mybatis :37-67 实证）——**ExecutorFilter 覆盖 10/15**（未覆盖 flushStatements/isCached/clearLocalCache/isClosed/setExecutorWrapper）；**挂载点**（Configuration.newExecutor → interceptorChain.pluginAll）；**双模型**（可干预 filter vs 纯观察 interceptor）
- **my-xhs**：该用没用——MP 覆盖

### 4.2 已证问题清单 P1-P6 [🟡 P2] [时间无关模式]
- **来源**：KP-1106（历史 13-01 :254）
- **机制**：P1 反射读私有字段（+G8 同族）/ P2 拼写 Defintion 第 7 例 / P3 初始 NPE 已修复 / P4 chain 重建可忽略 / P5 intercept 语义陷阱 / **P6 二级缓存短路**（CachingExecutor :102-107——命中不调 delegate——过滤器不执行——**拦截语义隐藏边界**）
- **my-xhs**：该用没用——**P6 直接适用**（MP 拦截器同样被二级缓存短路）

---

## 覆盖核对（6/6 + 对照）

| 维度 | 覆盖 KP | 数 |
|------|---------|:--:|
| 一、Executor 过滤链 | 1101,1102 | 2 |
| 二、Spring 与测试 | 1103,1104 | 2 |
| 三、生态对照 | 1105 | 1 |
| 四、问题域知识 | 1106 | 1 |

**去重后唯一 KP**：1101-1106 全部 = **6/6 ✓**
**无孤儿 KP** ✓
**历史对照**：13 篇 3 篇全映射（官方基线/问题清单 P1-P6/双模型/生态对照）——无遗漏 ✓
**拼写错误第 7 例**：MyBatisConfigurationBeanDefintionRegistrar（Defintion 少 i）
