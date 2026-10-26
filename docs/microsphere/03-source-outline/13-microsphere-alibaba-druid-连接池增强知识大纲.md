# Microsphere Alibaba Druid 连接池增强知识大纲（druid 触发面）

> 来源：`mapping/13-microsphere-alibaba-druid.md` 3 个 KP 的知识本体提炼
> 性质：**源码侧 outline（L1.5）**——mapping 是文件映射（过程），本大纲是知识本体（结果）
> 组织：按知识维度，非按文件；每个知识点标注来源 KP + 六元元数据 + my-xhs 落地实证
> 用途：①自学/面试知识图谱（连接池增强/接口收敛）②与课程 L1 合并成 L3 的源码侧素材 ③my-xhs 对照
> 覆盖核对：3/3 KP 全部归属（文末核对表）

---

## 一、接口收敛模板（核心命题）[工程问题]

> **核心命题**：Druid 官方 Filter **491 方法**（全生命周期）→ AbstractStatementFilter 收敛 **13 个** Statement 执行方法（final 强制路由）→ **2 个回调**（beforeExecute/afterExecute）——大接口的三层收敛。

### 1.1 收敛模板 [🔴 P1] [时间无关模式]
- **来源**：KP-1201
- **机制**：**适配器继承**（extends FilterAdapter——空实现 491 方法——只需覆写关心）+ **13 个 final 方法**（:125-135——统一路由进 execute 模板——**子类不可绕过**）+ **2 回调抽象**（最小实现）+ 内置样板（LoggingStatementFilter）
- **my-xhs**：该用没用——接口收敛模式可借鉴（适配大接口）

---

## 二、三源装配 + switch 缺陷 [工程问题]

### 2.1 三源扫描 + P2 fallthrough [🔴 P1] [时间无关模式]
- **来源**：KP-1202
- **机制**：**三源装配**（@EnableAlibabaDruid → BeanFactory/SpringFactories/JavaServiceProvider 三源扫描 Filter :67-79——02 仓库 SPI 体系应用）+ **BPP 排序注入**（DruidDataSourceBeanPostProcessor——初始化前塞 getProxyFilters :65/:81）
- **P2 缺陷（switch fallthrough）**：三个 case 全无 break（:69-78）——默认 sources 下 **BPP 注册两次**——**下游同名去重（allowBeanDefinitionOverriding=false 跳过+warn）才无害**——git 81b8043b 引入未修复——**测试未断言注册次数未捕获**
- **教训**：switch 必加 break；**"下游幂等掩盖缺陷"模式**（缺陷依赖下游容错）；测试要断言副作用次数
- **my-xhs**：该用没用——**switch 教训直接适用**

---

## 三、Boot 集成与池指标 [工程问题]

### 3.1 自动配置 + DataSourcePoolMetadata [🟡 P2] [时间无关模式]
- **来源**：KP-1203
- **机制**：自动配置（:52）+ **Actuator 池指标 SPI 实现**（DruidDataSourcePoolMetadata :48——getUsage/getActive/getIdle/getMax/getMin——**自定义连接池接入 Actuator 的标准路径**）
- **my-xhs**：已用——Hikari + Boot 官方池指标（同机制）

---

## 覆盖核对（3/3 + 对照）

| 维度 | 覆盖 KP | 数 |
|------|---------|:--:|
| 一、接口收敛 | 1201 | 1 |
| 二、三源装配 | 1202 | 1 |
| 三、Boot 集成 | 1203 | 1 |

**去重后唯一 KP**：1201-1203 全部 = **3/3 ✓**
**无孤儿 KP** ✓
**历史对照**：14 篇 2 篇全映射（491 基线/13 收敛/三源/P2 完整验证）——无遗漏 ✓
**my-xhs 对照**：已用 1 / 该用没用 2——**接口收敛 + switch 教训**为可迁移知识。

---

## 四、问题域知识（历史 14 篇 2 篇对照）[工程问题]

### 4.1 SQL AST 资源名 + 缺陷清单 P1-P4 [🔴 P1] [时间无关模式]
- **来源**：KP-1204（历史 14-01）
- **机制**：**SQL AST 解析**（SQLUtils.parseStatements :43——每条 SQL 解析生成资源名——SQL 级可观测键）；**P1 双开关冗余**（enabled 字段绑定但不实际开关 vs 条件注解——开关语义单一来源原则）；P3 spring-cloud 空壳（@Bean 0）；P4 日志占位符 3 对 2（:64）
- **my-xhs**：该用没用——SQL 级监控可借鉴

### 4.2 P2 根源：复用优先原则（跨仓库对照）[🔴 P1] [时间无关模式]
- **来源**：KP-1204（历史 14-02）
- **机制**：**同一 BeanSource 工具两种用法**——13-mybatis 复用静态工具（多态分派 :301-303——循环安全）vs 14-druid 手写 switch（:67-79——fallthrough bug）——**P2 = 不复用工具重造轮子的后果**——**复用优先原则**（自写替代已有工具前先查）
- **my-xhs**：该用没用——复用优先原则直接适用

---

## 覆盖核对（4/4 + 对照）

| 维度 | 覆盖 KP | 数 |
|------|---------|:--:|
| 一、接口收敛 | 1201 | 1 |
| 二、三源装配 | 1202 | 1 |
| 三、Boot 集成 | 1203 | 1 |
| 四、问题域知识 | 1204 | 1 |

**去重后唯一 KP**：1201-1204 全部 = **4/4 ✓**
**无孤儿 KP** ✓
**历史对照**：14 篇 2 篇全映射（P1-P4 缺陷/SQL AST/P2 根源对照）——无遗漏 ✓
