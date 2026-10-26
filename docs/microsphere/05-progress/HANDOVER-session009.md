# 交接文档 — L3 总教学大纲写作（Session 009）

> **本文件是 L3 总教学大纲写作的唯一权威进度文档。** 接手前完整阅读本文 + 方法论 + 写作指南，再动任何文件。
> 最后更新：2026-08-14
> 范围：L3 总教学大纲（30 主题文件 + 实验验证 + 总览终稿）——**33 篇主题 + 5 个维度索引 + 总览终稿全部完成！剩课程侧 L2 聚合 + 用户 review**

---

## 一、任务背景与全局状态

**目标**：L3 总教学大纲 = 最终交付物（07 SOP §0.1——按 5 大维度组织的完整教学大纲 + 每 KP 展开成教学章节）。形态已由用户确认为**"书章节"**（openjdk-book 标准：场景开场 + 人话先行 + 术语后置 + 边缘知识降级为"可跳过（进阶级）"）。

**全局项目状态**：

| 项目 | 状态 | 位置 |
|------|------|------|
| 课程提取 stage-1~4 | ✅ 103 篇 L1 | microsphere-extraction/progress/course/stage-{1..4}/ |
| 源码提取 microsphere-source | ✅ 14 仓 230 KP | microsphere-source/mapping/ + outline/ |
| 源码侧 L2 聚合 | ✅ 10 模式家族 + 5 维度 + 缺陷家族 | l2-aggregation/00-L2聚合-跨仓库知识图谱.md |
| 课程侧 L2 聚合 | ⬜ 未开始（L3 主题填充前置） | microsphere-extraction/index/ |
| **L3 主题文件** | **✅ 33 篇全部定稿（30 主题全覆盖）** | **l3-outline/** |
| L3 剩余 | ⬜ 仅用户逐篇 review（33 篇 + 5 索引 + 终稿 + L2 聚合） | l3-outline/ |
| 实验验证 | ✅ 29 模块 140 测试全绿（+spec-dbpool 4） | /data/workspace/sca-lab/custom-java-infra/ |
| my-xhs 差距清单执行 | 🔄 **完整合并版（33 项：原始 25 + L3 补充 8）**——执行未开始 | progress/my-xhs-差距清单-L3完整合并版.md |

**核心视角**：L3 不是"分析文档"——是**"知识 + 现代实现 + 自主实现 + 线上落地"的完整闭环**（方向规划定稿）：每个知识点 = 模式本体 + 现代载体（官方源码锚定）+ my-xhs 判定（已用/该用没用/不该用——代码实证）+ 实验断言。

---

## 二、目录结构与产出（l3-outline 4012 行）

```
microsphere-source/
├── discussion/2026-08-12-方向规划.md
├── method/01-现代实现映射与自主落地.md
├── mapping/  （14 仓 L1 提取表，230 KP）
├── outline/   （14 仓 L1.5 知识大纲）
├── l2-aggregation/00-L2聚合-跨仓库知识图谱.md
├── l3-outline/                          ← L3 主体（当前工作目录）
│   ├── 00-L3骨架-总览.md                  ← 骨架（103 篇主属表 + 填充计划 + 聚合线）——终稿阶段要更新
│   ├── 01-维度一-规范/    1176 行         ← 7 篇全部定稿（1.1~1.7）
│   ├── 02-维度二-分布式问题/ 1413 行      ← 10 篇全部定稿（2.1~2.10）
│   ├── 03-维度三-分布式理论/  479 行      ← 4 篇全部定稿（3.1~3.4）
│   ├── 04-维度四-工程问题/   ~1150 行      ← 8 篇全部定稿（4.1~4.8）
│   └── 05-维度五-性能优化/  ~725 行       ← 5.1~5.4 全部定稿
└── progress/
    ├── HANDOVER-session008.md            ← 上一份交接（源码提取阶段）
    └── HANDOVER-session009.md            ← 本文（L3 写作阶段）
```

### 已定稿 27 篇清单（章节编号 = 文章编号）

| 文章号 | 文件 | 主题号 | 行数 |
|:--:|------|:--:|:--:|
| 01 | 1.1-Java-SE规范.md | 1.1 | 240 |
| 02 | 1.2-Servlet规范.md | 1.2 | 197 |
| 03 | 1.3-JDBC规范.md | 1.3 | 210 |
| 04 | 1.4-BeanValidation规范.md | 1.4 | 163 |
| 05 | 1.5-MicroProfile规范.md | 1.5 | 94 |
| 06 | 1.6-Spring抽象规范.md | 1.6 | 128 |
| 07 | 1.7-云原生与协议规范.md | 1.7 | 144 |
| 08 | 2.1-服务发现与注册.md | 2.1 | 169 |
| 09 | 2.2-配置管理.md | 2.2 | 126 |
| 10 | 2.3-负载均衡.md | 2.3 | 141 |
| 11 | 2.4-服务容错.md | 2.4 | 133 |
| 12 | 2.5-分布式事务.md | 2.5 | 166 |
| 13 | 2.6-锁缓存与存储.md | 2.6 | 147 |
| 14 | 2.7-服务网关.md | 2.7 | 142 |
| 15 | 2.8-多活与区域路由.md | 2.8 | 142 |
| 16 | 2.9-可观测性.md | 2.9 | 124 |
| 17 | 2.10-消息与事件.md | 2.10 | 123 |
| 18 | 3.1-CAP与BASE.md | 3.1 | 106 |
| 19 | 3.2-共识算法.md | 3.2 | 136 |
| 20 | 3.3-分布式事务理论.md | 3.3 | 121 |
| 21 | 3.4-多活理论.md | 3.4 | 116 |
| 22 | 4.1-多模块与依赖管理.md | 4.1 | 119 |
| 23 | 4.2-自动装配与条件注解.md | 4.2 | 101 |
| 24 | 4.3-扩展点与SPI.md | 4.3 | 101 |
| 25 | 4.4-三层架构与工程构建.md | 4.4 | 114 |
| 26 | 4.5-Spring扩展机制.md | 4.5 | 89 |
| 27 | 4.6-设计模式.md | 4.6 | 112 |
| 28 | 4.7-工具与基础库.md | 4.7 | 353 |
| 29 | 4.8-框架抽象与适配.md | 4.8 | 216 |
| 30 | 5.1-容器与服务端.md | 5.1 | 164 |
| 31 | 5.2-JVM.md | 5.2 | 233 |
| 32 | 5.3-网络与IO.md | 5.3 | 175 |
| 33 | 5.4-数据库与缓存.md | 5.4 | 142 |

---

## 三、实验验证项目（24 模块 112 测试全绿）

位置：`/data/workspace/sca-lab/custom-java-infra/`（25 个 spec-* 模块——聚合父 pom + 每个主题一个实验模块）

| 模块 | 验证主题 | 断言数 |
|------|---------|:--:|
| spec-jdk | 01 SPI/资源束/Base64/空注解/日志 | 16 |
| spec-apt | 01 APT 三阶段闭环 | 3 |
| spec-servlet | 02 生命周期/Filter 链/异步 | 3 |
| spec-jdbc | 03 驱动/事务/隔离/Savepoint/DataSource/元数据 | 8 |
| spec-validation | 04 约束/级联/分组/自定义 | 5 |
| spec-microprofile | 05 ConfigSource/ordinal/转换 | 3 |
| spec-spring | 06 @AliasFor/@Conditional/Ordered/@Inject + 26 BPP/监听器/多播器 | 7 |
| spec-protocol | 07 Flow API/HTTP Interface | 2 |
| spec-discovery | 08 五类操作/租约/DiscoveryClient | 4 |
| spec-config | 09 Binder/热更新 | 3 |
| spec-loadbalancer | 10 平滑加权/动态权重/Zone 过滤 | 4 |
| spec-sentinel | 11 限流/熔断/热更新 | 4 |
| spec-tx | 12 传播/回滚/Outbox/TCC + 20 2PC 状态机 | 6 |
| spec-storage | 13 缓存三兄弟/SET NX 锁 | 4 |
| spec-gateway | 14 路由三要素/we:// | 4 |
| spec-zone | 15 ZoneContext/定位 SPI/区域优先 | 3 |
| spec-observability | 16 Counter/Timer/Gauge/Prometheus | 4 |
| spec-event | 17 分发器继承匹配/事务事件 | 3 |
| spec-theory | 18 CAP 分区模拟 | 3 |
| spec-consensus | 19 多数派/选举/复制 | 3 |
| spec-autoconfig | 23 三阶段/三源去重 | 3 |
| spec-spi | 24 选优/懒缓存/门控 | 3 |
| spec-patterns | 27 模板方法/错误聚合/责任链 | 3 |
| spec-tools | 28 类加载/调用者/进程/泛型/集合/版本/转换 | 11 |
| spec-bridge | 29 双栈/桥接/签名解析/特性开关 | 8 |
| spec-container | 30 Tomcat 并发/静态代理/Filter 缓存/双栈 | 5 |
| spec-jvm | 31 JFR 事件/线程池泄漏/强封装/内存模型 | 7 |
| spec-reactive | 32 Reactive Streams/背压/演进推导 | 4 |
| spec-dbpool | 33 ConcurrentBag/参数族/池指标/双池 | 4 |

**验证方式（必须 clean）**：`cd /data/workspace/sca-lab/custom-java-infra && mvn clean test`（全量 140 断言；APT 模块需先 `mvn install -DskipTests`——annotationProcessorPaths 不解析 reactor 内未安装 artifact）。

> **血泪教训（Session 009 深度 review 抓到）**：**非 clean 的增量构建会复用旧 target/classes + 旧 surefire 报告——曾导致"假全绿"**（spec-servlet 的 asyncSupported 修复从未真正编译验证）。**验收一律 `mvn clean test` 全量**，且以 `find . -path "*/surefire-reports/*.txt" | xargs grep -h "Tests run"` 汇总为准。

---

## 四、方法论铁律与写作标准（每篇必须遵守）

### 4.1 写作标准（书章节——用户多次纠正后的定稿形态）

1. **场景开场**：每篇第一段拉读者进场景（"你在 Spring Boot 里见过这行配置..."）
2. **人话先行**：术语后置（"DriverManager = 驱动们的登记处"）
3. **现代载体主讲**：**过时技术降级为"可跳过（历史对照）"**——用现代技术讲同一模式（教训：Eureka 被用户纠正——详见 §6）
4. **可跳过节**：边缘知识统一标"可跳过（进阶级）"——知识必须保留但不破坏可读性
5. **每主题结构**：头部（> 配套实验/定位/课程来源/前置）→ 场景 → 各节（为什么→是什么→怎么用→坑）→ 本章小结（知识本体 + 实验验证 + my-xhs 判定）→ 动手验证表格
6. **my-xhs 判定**：每篇必带（已用/该用没用/不该用——**代码实证**，禁止凭印象）

### 4.2 知识本体全覆盖（每篇写完的必做流程）

1. **词表终扫**（python 脚本）：把该主题的全部知识点词表（课程 KP 名 + outline 分节名 + 官方锚点类名 + my-xhs 实证名）扫一遍，缺失即补
2. **行号实证**：所有 file:line 写作时 grep 抄录（禁止估算——教训：Nacos 心跳数字 15s→5s）
3. **实验必配**：每主题至少 3 断言实验（mvn test 绿）

### 4.3 常见坑（全部踩过——写作时对照）

| 坑 | 表现 | 解法 |
|----|------|------|
| python 引号冲突 | 含中文引号字符串的 heredoc 语法错误 → 整段补丁静默丢失 | 用 edit 工具或转义；补丁后必须词表终扫确认 |
| heredoc 时序 | 目录刚 mkdir 就 heredoc → "No such file" | 先 mkdir 再写入（或重试） |
| grep 误报 | 全角/半角括号、压缩写法（stage-2-02、03）导致词表"缺失" | 判定前先 grep 实际文本 |
| record 不能继承 | 事件类用 record 子类化编译错 | 用普通类 |
| static @Bean 访问实例方法 | 编译错 | 用静态列表 |
| BPP 对每个 Bean 执行 | 断言次数不符 | 断言成对（before=after）而非具体数 |
| 有状态 Chain | 责任链复用 index 泄漏 | 每次调用重建链 |
| DegradeRule timeWindow | 规则被 isValidRule 静默拒绝 | 必须 setTimeWindow（真实使用坑） |
| @HttpExchange method 属性 | 是 String[] 非 RequestMethod | 用字符串 |
| micrometer 1.13 包名 | prometheus → prometheusmetrics | 查 jar 内包名 |
| Binder 构造 | Boot 3.3 签名不同 | 用 Binder.get(env) |
| FilterRegistration.Dynamic.setAsyncSupported | 返回 void——链式编译错 | 分步（先拿 Dynamic 再设置） |
| 编程注册 Servlet 的 asyncSupported | @WebServlet 注解对编程注册无效——默认 false | 显式 `async.setAsyncSupported(true)` |
| 断言写反 | "业务线程应是 http-nio"（实际应是 pool-） | 断言前先想清楚语义 |
| 非 clean 验证假绿 | 增量构建复用旧 target/报告 | 一律 `mvn clean test` 全量验收 |

---

## 五、剩余工作（按顺序）

### 5.1 立即（维度四收尾）
1. ~~**4.7-工具与基础库.md**（文章 28）~~ **✅ 已定稿（352 行，深度 review 两轮）**——素材：源码 01 一~三/五/六章 + 02 三/四/六/七/八章 + 06 五章 + 10 三章；新实验 `spec-tools`（11 断言全绿）；my-xhs 判定 3 处反射实证（RocketMQ 该整改/BoundSql 业界惯例/代理无风险）+ GracefulShutdown 关闭钩子实证 + LRU"决策不用"Caffeine 实证
2. ~~**4.8-框架抽象与适配.md**（文章 29）~~ **✅ 已定稿（216 行，深度 review 轮补漏）**——素材：09 四章双栈 + 11 二章 Filter 桥接 + 12 1.2 签名解析 + 08 二/三章选型框架 + 04 四章兼容层/Actuator 化 + 05 1.2/五章 + **课程主属 stage-2-22（ServiceRegistry 接口抽象——首轮遗漏已补）**；新实验 `spec-bridge`（8 断言全绿）；my-xhs 判定：官方 Nacos Starter 已用（pom:52/:58）/ logback 原生覆盖 Filter 抽象

### 5.2 维度五（5.1~5.4，4 篇）
- ~~5.1 容器与服务端~~ **✅ 已定稿**（164 行，深度 review 轮补漏——stage-3-05/1-21/1-22/3-13 全 KP + 09 二章；实验 spec-container 5 断言）
- ~~5.2 JVM~~ **✅ 已定稿**（233 行，深度 review 轮补漏——stage-3-04 全 KP + 18 全 KP 含内存 10 项/NMT + 28/31/32/33 + 01 六章归组替代；实验 spec-jvm 7 断言）
- ~~5.3 网络与IO~~ **✅ 已定稿**（175 行，深度 review 轮补漏——stage-3-17 全 8 KP + IO 源码面 FastByteArray/FileWatchService + Netty 定位；实验 spec-reactive 4 断言）
- ~~5.4 数据库与缓存~~ **✅ 已定稿**（142 行，深度 review 轮补漏——07 7.1 全参数含 idleTimeout 生效前提/keepaliveTime + 13 三/四章 + 07 5.1；实验 spec-dbpool 4 断言）

**✅ 30 个主题文件全部完成！剩余：5 个维度索引 + 总览终稿 + 课程侧 L2 聚合**
- 5.4 数据库与缓存（源码 13 三章 + 07 7.1 Hikari 参数族 + 交叉）
- 每篇仍需实验（建议：5.4 可引用 spec-jdbc/spec-storage）

### 5.3 收尾
1. 5 个维度索引文件（00-维度索引.md——维度定位 + 主题清单）
2. **总览终稿**（00-L3骨架-总览.md 升级）：教学路径 + 缺陷家族总表 + 来源核对表 + 序言——**更新 103 篇主属表（1.5 PACELC 已实证非缺口——已同步）**
3. 课程侧 L2 聚合（轮 0——4 份完整梳理文档为输入：training-camp/stage-{1..4}-完整梳理.md）
4. 每篇的"深度 review"轮次（用户习惯：每篇定稿后要求 1-2 轮词表终扫 + 深层知识点补漏——写完后主动 review）

---

## 六、用户纠正记录（重要教训——不要重犯）

1. **"这算文章吗？"**（Session 008 尾）——1.1 初版是知识点卡片（AI 味）被否 → 重写为书章节（openjdk-book WRITING-GUIDELINES 标准）
2. **"一个没看懂"**——1.3 等术语轰炸被否 → 三篇全部重写为"场景 + 人话 + 可跳过节"
3. **"为啥又讲解过时的 Eureka？"**——2.1 原计划 Eureka 主讲被否 → **现代载体（Nacos）主讲，Eureka 降为可跳过节**
4. **"ZK 没过时吧？"**——我误判 ZK 过时被纠正 → **ZK 是活跃技术（curator 5.8.0 + Kafka/HBase/Dubbo 依赖实证）**；过时的只是"ZK 当注册中心"的选型场景——**精确过时判定（04 方法论）**
5. **"后面的内容不会这么少吧？"**——维度二（66 篇主属）体量质疑 → 每篇 120-170 行、大主题（2.8）拆子轮
6. **"项目分类来搞，不能纸上谈兵"**——要求实验验证 → 建立了 custom-java-infra（每个知识点 = 代码 + 断言）
7. **"JPA/安全体系不要"**——规范面排除 JPA 与 OAuth/OIDC（§9.4 决策记录）
8. **"再深度 review/查漏补缺"**——每篇至少 1 轮额外词表终扫 + 深层补漏（当前 27 篇都已做）

---

## 七、交叉引用约定

- **文章编号 = 教学章节号**（01~30）：主题文件头部写文章号（如 2.1 = 文章 08）
- 交叉引用用文章号 + 主题号（"2.1 章节"、"1.6 章节"）
- 源码引用格式：`源码 03 二章`（outline 分节）/ `mapping KP-xxx`
- 课程引用格式：`stage-3-09`（提取文档编号）
- my-xhs 实证：一律注明代码位置（`my-xhs-common/.../ZoneContext.java` 实证）

---

## 八、git 约定

- 仓库：`/data/workspace/source-code/book/成长之路`，分支 `fresh`
- 只提交 l3-outline/ 相关文件；不碰 source-analysis/issue、talk-method 等他人未提交改动（git status 中始终存在——勿 add）
- 本会话未提交（用户未要求）

---

## 九、接手须知（下个 AI 第一件事）

1. **必读**：本文 + `method/01-现代实现映射与自主落地.md` + `discussion/2026-08-12-方向规划.md` + `l2-aggregation/00-L2聚合-跨仓库知识图谱.md` + openjdk-book 的 `WRITING-GUIDELINES.md`（书章节标准）
2. ~~**5.1 容器与服务端**~~ **✅ 已定稿**——素材：stage-3-05 容器调优 + stage-1-21/22 Web/Cloud 性能 + stage-3-13 双栈 + 源码 09 二章 Filter 缓存；新实验 `spec-container`（5 断言全绿）；my-xhs 实证：Tomcat 差异化线程数（gateway 300/user 150 + 三特征分类注释）/ WebFlux 网关 / G1+200ms（start-all.sh:30-34）
3. ~~**5.2 JVM**~~ **✅ 已定稿**——素材：stage-3-04 JFR + 18 生产故障 + 28/31/32 Native 三路径 + 33 Java 演进 + 01 六章归组替代 + 02 4.1；新实验 `spec-jvm`（5 断言全绿）；my-xhs：JDK17 标准栈已用/异步池化已用/JFR 该用没用/虚拟线程迁移待评估
4. ~~**5.3 网络与IO**~~ **✅ 已定稿**——素材：stage-3-17 全 8 KP + IO 源码面（02 六章 FastByteArray/FileWatchService——首轮遗漏已补）+ Netty 锚点；新实验 `spec-reactive`（4 断言全绿）；my-xhs：网关 WebFlux 已用/订单 CompletableFuture+MDC 线程池已用
5. ~~**5.4 数据库与缓存**~~ **✅ 已定稿**——素材：源码 07 7.1（ConcurrentBag/参数族）+ 13 三章（DataSourcePoolMetadata）+ 13 四章（SQL AST）+ 07 5.1（驱动推导）；新实验 `spec-dbpool`（4 断言全绿）；my-xhs：Hikari 五参数族 + 读写分离双池（20/10）完整实证
6. ~~**收尾**~~ **✅ 完成**——5 个维度索引（00-维度索引.md×5）+ 总览终稿（00-L3总览终稿.md——序言/6 阶段教学路径/缺陷家族总表含拼写 4~7 例补齐/覆盖核对/实验总览 140 断言实证）
7. ~~**课程侧 L2 聚合（轮 0）**~~ **✅ 完成**——`microsphere-extraction/index/00-课程侧L2聚合-跨stage知识图谱.md`（30 功能域全部承接 + 35 组现代替代映射全部落地——本轮补 OpenTelemetry 全称/@Observed/Hystrix 对照/RSocket 衰退定位/Cilium/Proxyless/Actuator Endpoints/P1 时序 2.10 交叉 8 处）
8. **收尾遗留**：仅用户逐篇 review（33 篇 + 5 索引 + 总览终稿 + L2 聚合）
9. **深度 review 轮（2026-08-15）**：✅ 三系统检查——①实验断言名全量核对（138 候选中 136 断言 + 2 误判——全部真实存在）②骨架 §9 批注落实核对（补 5 处：JPA 排除 1.4/OpenMetrics+SLF4J+ECS 2.9/雪花 ID 2.6（IdGeneratorUtil 三合一实证）/定时任务 4.8（CouponOutboxSenderJob 实证））③全量行号抽查（52 文件——**抓到 Nacos 心跳参数错误**：原引 naming Constants.java:189/:187 实为 api/common Constants.java:189/:185/:187——15s 心跳超时 vs 30s IP 删除超时——2.1 已修正）
10. **my-xhs 差距清单（L3 汇总）**：✅ 清单完成（`my-xhs-差距清单-L3汇总.md`——P1 3 项/P2 8 项/P3 10 项 + "不该用"防误入表）——**核实抓到 3 处文章判定过时并修正**：①5.1"Filter 链缓存该用没用"→ **已用**（CachingFilteringWebHandler.java:36/:58/:71-73——同构源码 09 且规避 G8 坑）②1.7"K8s 该用没用"→ **部分已用**（k8s/ 8 个部署模板——上真集群为演进项）③4.8"分布式调度未引入"→ **已引入**（XxlJobConfig + FollowCounterRepairJob——analytics 模块）——差距清单同步更新
11. **深度 review 轮 2（2026-08-15）**：✅ 文档质量系统检查——①结构完整性（33 篇 8 要素全——1.2/1.3 标题统一）②文章编号 1-33 连续 ③实验模块对应全真 ④写作标准（围栏/表格/可跳过 29/33——4 篇全核心合理）⑤索引断言数修正（spec-spring 4→7 两处）⑥**动手验证表 123 断言精确核对——全部真实存在**（10 误报均为描述列）⑦正文/小结判定一致性（扫描误报——判定节在小结下的正常结构）
12. **my-xhs 关系图谱（2026-08-15）**：✅ `microsphere-source/my-xhs关系图谱.md`——探索两项目关系：①my-xhs = L3 落地卷（三重角色）②**移植对照**（Zone 机制逐字段实证：微球 instance→my-xhs INSTANCE、7 volatile 属性精确对应；CachingFilteringWebHandler 自主实现零反射规避 G8；CosId 2.6.8 号段实证）③技术栈映射表（16 项已用）④差距面（P1 3 项）⑤不该用面（7 项防误入）⑥关系总结（提取→聚合→教学→落地闭环）
13. **my-xhs 关系图谱轮 2（2026-08-15）**：✅ 深化代码级对照——⑦**方法级同构**（ZonePreferenceFilter 六方法逐行对应——filter 七级流程前 4 级逐行、就绪率整数除法语义保留、my-xhs 自主补 matches）⑧**缺陷传播修正**（微球 PREFERENCE_FILER 拼写→my-xhs 已修正；常量接口→@ConfigurationProperties；ZonePreferenceFilterTest 14 断言补微球测试缺口）⑨**自主增量**（chaos 包=2.8 Chaos 反模式落地/ApiVersionCondition=4.7 SemVer 落地/ETag/CounterBuffer 等）⑩**业务链路落地**（网关 8 Filter/OrderService Outbox/CacheHelper 三兄弟全防/PayStrategy 策略）⑪**测试关系**（L3 140 断言验证知识 + my-xhs 59 测试文件验证落地——同一知识点两道验证）
14. **自定义指标探索（2026-08-15——用户指出"小马哥自定义指标"）**：✅ 核实——**cgroup 指标教学层遗漏已补**：源码 11 CGroupMemoryMetrics（cgroup 直读 + @ConditionalOnResource 容器探测）在 outline 有但 2.9 未展开（只有一句提及）→ **2.9 补"自定义 MeterBinder"节**（cgroup 两族指标 + 容器探测 + 系统/JMX/Sentinel/JDBC binder 族）+ 差距清单新增 P2-I；**动态权重核实**：2.3 §3 已覆盖（WeightedResponseTimeRule 思路 + 实验）+ **sca-lab gateway-lab 落地实证**（WeightedResponseTimeGatewayFilter——weight=1/(avgMs+1) + RingBuffer 滑动窗口 + 冷启动退化 + order>10150 决策注释）——2.3 已补 lab 关联
15. **反向全量核对（2026-08-15——outline 分节 → L3 覆盖）**：✅ 66 候选过滤后 6 项核实——**4 处实质修复**：①移植来源第三例（Android JSON 版权头——JSON.java:2 实证）补 1.1 ②"设计文档式代码"反模式（I18nLogger 空实现/MBeanAttributeMeterBinder 骨架——11 仓 4.2）补 2.9 ③**G1-G15 进度自我纠错**（先误改 10/15——outline 实际已补证 15/15（:108）——改回并补协议隐患三连 G11/G12/G3 到 2.7）④REQ 编号/D09 待验证项**不转述**（教学层不保留未证实历史编号——诚实标注原则）
16. **差距清单重建（2026-08-15——用户质疑"只有这么点"）**：✅ 发现原清单严重不完整——**原始 `my-xhs-优化规划.md` 有 25 项（P1 2/P2 8/P3 15）编号体系，L3 汇总清单只收录 22 项且两套未合并、漏掉原始项** → 重建**完整合并版 33 项**（`my-xhs-差距清单-L3完整合并版.md`——原始 25 项全保留 + L3 补充 8 项 P1-A/P1-C/P2-A/B/C/D/I/P3-A）——验证 25/25 + 8/8 全部收录
17. **引用完整性核对（2026-08-15）**：✅ ①my-xhs 实证文件 56 类全存在 + 15 处行号在范围内 ②stage-x-yy 课程引用 134 个全存在（2 次脚本 bug 排除）③**测试类引用修正 1 处**（3.3 TwoPhaseTest→TxSpecTest——方法实际在 TxSpecTest.java:107/:118）④29 实验模块无孤儿（全被文章引用）⑤53 官方源码引用全存在（2 处 find 误报排除）⑥总览终稿数字全准（33 篇/5 索引/29 模块/140 断言）⑦维度索引声称 vs 实际全一致 ⑧骨架总览加"历史状态注记"（过程文档 vs 交付物区分）
3. 每篇写完：`mvn -pl <模块> test` 绿 + 全量 `mvn clean test` 绿 + 词表终扫全过 + 交用户 review
4. 流程纪律：**一次只做 1 个主题文件**（禁止批量——用户多次强调）
5. 实验模块命名：`spec-<主题>`（英文短名）——新增模块要加进根 pom 的 modules
