# Microsphere Java 生态设计模式知识大纲（microsphere-java 触发面）

> 来源：`mapping/02-microsphere-java.md` 53 KP + 深度 review 补提取（KP-120a/b/c 事件系统/IO 族/classloading 策略族）
> 性质：**源码侧 outline（L1.5）**——mapping 是文件映射（过程），本大纲是知识本体（结果）
> 组织：按知识维度，非按文件；每个知识点标注来源 KP + 六元元数据 + 现代替代
> 用途：①自学/面试知识图谱 ②与课程 L1 合并成 L3 的源码侧素材 ③confucius outline 的姊妹篇
> 覆盖核对：53/53 KP 全部归属（文末核对表）

---

## 一、SPI 生态模式——"统一注册中心"（microsphere 的核心 DNA）[工程问题]

> **核心命题**：microsphere 生态最强的模式 = **SPI 自动发现 + Prioritized 选优 + 缓存**。convert/io/metadata 三处独立实证——这是它的"注册中心标准件"。

### 1.1 SPI 加载器演进（confucius → microsphere）[🔴 P1] [时间无关模式]
- **来源**：KP-123 + confucius KP-32
- **机制**：`ServiceLoaderUtils.loadServicesList` **4 重载**（:382/:410/:438/:468）+ **缓存开关**（servicesCache :140 + `microsphere.service-loader.cached` 系统属性 :112）——confucius 版无缓存（每次重载），本版加缓存层——**同主题两代实现的演进实证**
- **现代替代**：JDK9+ `ServiceLoader.stream()`（lazy）
- **my-xhs**：不该用（Spring SpringFactoriesLoader 覆盖）；机制必须掌握

### 1.2 "SPI + Prioritized 选优"注册中心模式（三处实证）[🔴 P1] [时间无关模式]
- **来源**：KP-123（Converters）/ KP-141（Serializers）/ KP-144（ConfigurationPropertyLoader）
- **机制**：统一三步——①`loadServicesList(接口.class)` SPI 加载全部实现 ②`Prioritized`/`COMPARATOR` 排序（或按类型键分组）③取最优（first/getHighestPriority）——**convert（转换器）/io（序列化器）/metadata（元数据加载器）三处同构**
- **对比取舍**：microsphere 是 **SPI 自动发现式**（classpath 扫描免注册）vs Spring `ConversionService`/`ObjectProvider` 的**注册式**——架构对照
- **my-xhs**：该用没用——自定义协议转换/扩展层可参考（如 my-xhs 的 SPI 化适配器）

### 1.3 双键缓存 + 懒过滤（Converters 专用）[🔴 P2] [时间无关模式]
- **来源**：KP-123（Converters:42-77）
- **机制**：`Entry<Class,Class>` 双键缓存（:55）+ 启动全量分组（:48-60）+ **未命中时按 accept 懒过滤再缓存**（:64-67）——"启动分组 + 懒补充"双层策略
- **my-xhs**：该用没用——"按组合键缓存 + 懒匹配"模式（任何"类型对→处理器"场景）

---

## 二、注解与编译期体系——配置元数据三阶段闭环 [规范]

> **核心命题**：microsphere 的招牌设计 = **注解声明 → 编译期生成 JSON → 运行期加载**的三阶段闭环（Spring 无编译期环节）。

### 2.1 三阶段闭环总览 [🔴 P1] [时间无关模式]
- **来源**：KP-101 → KP-146 → KP-144
- **机制**：①**声明**（@ConfigurationProperty 六要素注解——name/type/defaultValue/required/description/source，KP-101:38-97——**显式元模型** vs Spring 约定推断）②**编译期**（annotation-processor 4 类——ConfigurationPropertyAnnotationProcessor 生成 JSON，KP-146）③**运行期**（ConfigurationPropertyLoader SPI 链加载 JSON → beans.ConfigurationProperty 对象，KP-144:47-97 + KP-109:63-252——**注解↔运行时对象双层设计**）
- **对比取舍**：**编译期 vs 运行期**——编译期方案（类型安全 + 启动快无反射）vs Spring Binder（运行期反射、约定推断）——**架构取舍核心**：显式元模型 vs 约定
- **my-xhs**：该用没用——若 my-xhs 做自定义配置注解框架

### 2.2 编译期资源约定（ResourceConstants）[🟡 P2] [时间无关模式]
- **来源**：KP-105（ResourceConstants:43-60）
- **机制**：`META-INF/microsphere/configuration-properties.json` + additional 附加文件——**编译期产出物与运行期读取物的文件约定**
- **my-xhs**：该用没用——自定义配置框架的文件约定设计

### 2.3 注解家族与来源分层 [🟡 P2] [过时→JSpecify/Spring]
- **来源**：KP-102/103/104
- **机制**：空注解（TypeQualifierNickname 组合）、线程安全标注（**JCIP 移植**——Brian Goetz 版权头实证）、@Since（module+value 属性）
- **移植来源教训**：javax.annotation.concurrent 是 **JCIP**（非 JSR-305）——来源分层是移植识别的实证手段
- **my-xhs**：不该用（Spring Nullable 覆盖）

---

### 2.4 编译期注解处理器（APT 完整生命周期）[🔴 P2] [时间无关模式]
- **来源**：KP-146a/146b/146c
- **机制**：**APT 生命周期**（@SupportedAnnotationTypes :77 → init :89 → process 每轮 → Filer 输出 JSON :64）+ **Element → JSON 访问器**（JSONElementVisitor/JSONAnnotationValueVisitor——编译期元数据化基础设施）+ lang-model 工具家族（ElementUtils/TypeUtils/MessagerUtils）
- **对比**：编译期（类型安全+启动快）vs Spring 运行期反射；三阶段闭环（注解 → 编译期 JSON → 运行期加载）
- **my-xhs**：该用没用——自定义注解处理器参考

### 2.5 元注解家族（@Since/@Experimental/@Immutable + 编译期资源约定）[🟢 P3] [时间无关模式]
- **来源**：KP-104
- **机制**：@Since（module+value 属性）/@Experimental（SOURCE retention）/@Immutable（RUNTIME）——库作者文档工具
- **my-xhs**：不该用——内部库无需



> **核心命题**：泛型类型模型（JavaType）+ 实参解析（TypeUtils）是 convert 泛型推断的地基——**本包与 convert 的依赖链实证**。

### 3.1 `JavaType` 类型模型——JDK Type 5 形态封装 [🔴 P1] [时间无关模式]
- **来源**：KP-125（JavaType:92-1329）
- **机制**：Kind 枚举（CLASS/PARAMETERIZED_TYPE/TYPE_VARIABLE/WILDCARD_TYPE/GENERIC_ARRAY_TYPE + UNKNOWN）+ 懒解析缓存（interfaces volatile :342-348）+ 常用实例预置（OBJECT/NULL）
- **现代替代**：Spring `ResolvableType`（更全含 AnnotatedType）
- **my-xhs**：该用没用——泛型工具参考（泛型 DAO 等）；ResolvableType 覆盖

### 3.2 `TypeUtils` 实参解析链 [🔴 P1] [时间无关模式]
- **来源**：KP-126（TypeUtils:448-493）
- **机制**：resolveActualTypeArguments → resolveActualTypeArgument → 类版本——**convert 包 `getSourceType()` 的泛型推断地基**（KP-121:99-110）
- **现代替代**：Spring `GenericTypeResolver.resolveTypeArgument`
- **my-xhs**：该用没用——泛型擦除与实参解析机制是必懂知识

### 3.3 反射工具设计三件 [🟡 P2] [时间无关模式]
- **来源**：KP-128（MethodUtils/FieldUtils/ConstructorUtils/AccessibleObjectUtils）+ KP-127（定义类家族）+ KP-129（ProxyUtils）
- **机制**：**Predicate 工厂**（PUBLIC/STATIC/FINAL 等组合过滤 :132-157）+ **banned-methods 配置化禁用名单**（:113/:252）+ **AccessibleObjectUtils --add-opens 友好提示**（错误解析自动提取包名 :200-204）+ 定义类家族（@Immutable 元数据模型）
- **my-xhs**：该用没用——banned 名单 + add-opens 提示模式（开发体验设计）

### 3.4 Bean 属性解析核心（深度预算防环）[🔴 P1] [时间无关模式]
- **来源**：KP-108（BeanUtils:449-514）
- **机制**：**递归解析 + 深度预算防环**（`incrementAndGet() >= maxResolvedDepth` 截断 :453——无 visited 集，深度耗尽即停）+ **类型白名单**（primitive/simple/Class 直接返回 :487-495）+ BeanMetadata 并发缓存（:150）
- **对比**：深度预算 vs Jackson/Spring 对象标识防环——性能权衡
- **my-xhs**：该用没用——属性展平/导出场景；循环引用注意

---

## 四、集合与工具——"JDK 补位 + 演进兼容" [性能优化/工程问题]

> **核心命题**：JDK9 前补位（of 工厂）+ 演进兼容（MethodHandle 探测）+ 性能细节（加载因子）三主题。

### 4.1 MethodHandle 版本探测模式 [🔴 P1] [时间无关模式]
- **来源**：KP-117/118（Lists/Sets/Maps 三文件实证）+ invoke 包（MethodHandlesLookupUtils）
- **机制**：**探测-降级双保险**——`findPublicStatic` 运行期探测 JDK9 `List.of`/`Set.of`/`Map.of`（存在则 invokeExact，否则降级 Arrays.asList）；方法不存在返回 NOT_FOUND（:114-115）+ Lookup 失败返回 null 带 trace（:125-129）；Sets/Maps **按 arity 缓存多 handle**（of0/of1...）
- **对比取舍**：跨 JDK 版本兼容的标准手法——**现代替代**：直接 `List.of`（JDK9+）
- **my-xhs**：不该用（List.of 原生）；模式可参考（跨版本兼容）

### 4.2 重载轰炸与集合语义 [🟡 P2] [过时→List.of]
- **来源**：KP-116/117/118/119/120
- **机制**：9 重载避免 varargs 分配（JDK9 前手法）+ **equals 集合语义**（跨类型相等——List vs Set 空集相等测试实证）+ **FIXED_LOAD_FACTOR=1.0f 防扩容**（MapUtils:61-66/:965-1006——已知容量场景）+ accessOrder 参数化（LRU :655）
- **my-xhs**：该用没用——LRU 缓存（accessOrder）；equals 语义陷阱注意

### 4.3 空/单例/不可变/委托/适配器五家族 [🟡 P2] [过时→Collections/List.of]
- **来源**：KP-110~114
- **机制**：空对象 + 委托 + 单例三模式组合；**DelegatingWrapper 解包协议**（unwrap/isWrapperFor 默认方法——JDBC Wrapper 模式移植）
- **my-xhs**：该用没用——DelegatingWrapper 解包协议（缓存/连接包装层）

### 4.4 集合工具细节（equals 语义/工厂/专用工具）[🟡 P2] [过时→List.of]
- **来源**：KP-115（Iterators.equals/PropertiesUtils.flatProperties）+ KP-118（Sets.ofSet/Maps.ofMap arity 缓存）+ KP-119（MapUtils）+ KP-120（Queue/Set/List 专用）
- **机制**：**equals 集合语义**（跨类型相等——List vs Set 空集相等测试实证；multiset 边缘缺陷）+ **按 arity 缓存多 MethodHandle**（Sets of0/of1...）+ FIXED_LOAD_FACTOR=1.0f（防扩容）+ accessOrder（LRU）
- **my-xhs**：该用没用——LRU 缓存参考；equals 语义陷阱注意

### 4.5 Bean 元数据缓存与属性包装 [🟡 P2] [时间无关模式]
- **来源**：KP-106/107
- **机制**：Introspector 缓存（BeanMetadata）+ 属性值包装（BeanProperty——@Nonnull/@Nullable 空语义）
- **my-xhs**：该用没用——高性能反射属性访问参考；Spring BeanWrapperImpl 有内置缓存

---

## 五、事件与日志 [工程问题]

### 5.1 事件分发器——类型键缓存 + 继承匹配 [🔴 P1] [时间无关模式]
- **来源**：KP-136/137/138
- **机制**：**类型键缓存**（ConcurrentMap<Class, List<Listener>>）+ **isAssignableFrom 继承匹配**（父监听器接子事件 :165）+ ConditionalEventListener 条件过滤 + **写入时持锁重排**（:199-207）+ SPI 自动加载 + Executor 策略注入（direct/parallel）
- **对比取舍**：Spring 用 ResolvableType 精确匹配 vs microsphere 的 isAssignableFrom 宽松匹配
- **my-xhs**：该用没用——轻量事件模型；Spring Event 覆盖主流

### 5.2 日志门面——SLF4J 模式复刻 [🟡 P2] [过时→SLF4J]
- **来源**：KP-139
- **机制**：**门面选择三要素**（SPI 加载 + isAvailable 过滤 + Prioritized 排序→get(0)）+ Logger 五级接口 + 4 实现
- **对比取舍**：**重造轮子判断**——SLF4J 已成熟，microsphere 复刻是低价值
- **my-xhs**：不该用（SLF4J + Logback 现状）

---

## 六、版本与兼容 [工程问题]

### 6.1 Version 语义化版本 [🟡 P2] [时间无关模式]
- **来源**：KP-132（Version:91-791）
- **机制**：SemVer Comparable + 重载工厂 + **被定义类家族引用**（MemberDefinition since）
- **my-xhs**：该用没用——版本兼容判断；Spring Boot Version 覆盖

### 6.2 Compatible 版本条件执行 DSL [🔴 P2] [时间无关模式]
- **来源**：KP-133（Compatible:83-272 + Version.java:671 Operator）
- **机制**：**声明式版本分支**——`Compatible.of(Class).on(">=", "2.0").call()` 链式 DSL；**Operator 枚举 implements BiPredicate**（EQ/LT/LE/GT/GE + null 安全比较）
- **对比取舍**：Spring 无直接等价——**独特设计**（Java 手写 if 的 DSL 化）
- **my-xhs**：该用没用——多版本兼容场景（第三方 SDK 适配）

---

## 七、类加载与构件探测 [工程问题]

### 7.1 Artifact 探测——"从类反查构件" [🔴 P2] [时间无关模式]
- **来源**：KP-142（ArtifactDetector:76-137）
- **机制**：类 → classpath URL → Artifact（artifactId/version/location）+ Resolver SPI 按 URL 类型分派（Maven 从 pom.properties 解析版本）+ **BannedArtifact 黑名单**（META-INF/banned-artifacts——与 banned-methods 同家族）
- **my-xhs**：该用没用——依赖冲突诊断；Maven dependency:tree 是构建期替代

### 7.2 URL 协议扩展——组合工厂解决单例限制 [🔴 P2] [时间无关模式]
- **来源**：KP-143（CompositeURLStreamHandlerFactory:56-132）
- **机制**：`URL.setURLStreamHandlerFactory` **全局单例限制** → 组合工厂（多工厂顺序委派）+ SPI 加载 + 子协议连接工厂
- **对比取舍**：扩展 JDK URL 协议 vs Spring `Resource` 抽象——架构取舍
- **my-xhs**：不该用（Spring Resource 覆盖）

### 7.3 关闭钩子管理 + 流工具 + 序列化 SPI [🟡 P2] [时间无关模式]
- **来源**：KP-134（ShutdownHookUtils）/ KP-140（IOUtils）/ KP-141（Serializer）
- **机制**：**关闭钩子专用线程 + 容量配置化**（shutdown-hook.callbacks-capacity :86-107——防单回调阻塞/无限注册）；IOUtils 缓冲区三层配置（microsphere.io.buffer.size）；Serializer SPI（"SPI + Prioritized 选优"第三处实证——convert/io/metadata 同构）
- **my-xhs**：该用没用——关闭钩子管理（清理任务）；Spring @PreDestroy 覆盖多数

### 7.4 工具包（JSON 移植 + 并发工厂 + 小包）[🟡 P3] [过时→Jackson]
- **来源**：KP-145a/145b/145c
- **机制**：**JSON = Android OpenJDK 移植**（版权头 "Copyright 2010 Android" 实证——第三例移植来源）+ **CustomizedThreadFactory 定制化**（命名/守护/优先级/栈大小 :30-34）+ MethodHandlesLookupUtils（**KP-117 探测模式实现地**）+ filter（confucius KP-13~18 同主题）
- **my-xhs**：该用没用——线程命名规范；Jackson 覆盖 JSON

---

## 八、类型转换方向族（convert 实现层）[工程问题]

> **核心命题**：52 个转换器按"转换方向"归 4 族——数值/字符串/Object/集合——每族一个模板模式。

### 8.1 字符串转换族（String 枢纽落地）[🔴 P2] [时间无关模式]
- **来源**：KP-124b（StringTo* 17 个）
- **机制**：`StringConverter<T> extends Converter<String,T>`（String 枢纽标记）+ 单例模式（INSTANCE）+ **JDK8 类型支持**（StringToDuration/InputStream——Spring 需自定义）
- **my-xhs**：该用没用——配置格式转换（String→任意）

### 8.2 集合转换族（继承树模板）[🔴 P2] [时间无关模式]
- **来源**：KP-124d（multiple 14 个）
- **机制**：**继承树而非独立类**（StringToIterableConverter 基座 → Collection → List/Set/Queue/Deque 13 变体）+ MultiValueConverter 双类型 accept（源 + 集合类型双匹配）
- **my-xhs**：该用没用——分隔字符串转集合

### 8.3 数值族 + Object/特殊族 [🟡 P3] [时间无关模式]
- **来源**：KP-124a/124c
- **机制**：数值族（Number→X 7 个——统一模板）；Object 宽入口 + Optional 特殊实现（implements Converter 非 Abstract）+ Map↔Properties 互转
- **my-xhs**：不该用——Spring NumberUtils 覆盖

---

## 九、生态演进与移植来源实证（跨仓库总结）[工程问题]

### 9.1 两代演进对照表（confucius → microsphere-java）[🟡 P2]
| 主题 | confucius | microsphere-java | 演进 |
|------|-----------|-----------------|------|
| SPI 加载 | 无缓存（KP-32） | 缓存开关 + 4 重载（KP-123） | 性能 |
| ClassLoaderUtils | 基础（KP-01~05） | null 安全 + 缓存 + 调用者感知（KP-130） | 健壮性 |
| 调用者类解析 | sun.reflect.Reflection（KP-28） | getStackTrace 遍历（KP-130 StacKTrace） | 兼容（仍未用 StackWalker） |
| 常量接口 | 平铺 5 接口（KP-11） | 分层聚合 + JDK 内置（KP-105） | 工程化 |
| 空注解 | JSR-305 直接依赖（KP-12） | TypeQualifierNickname 组合（KP-102） | 标准化 |

### 9.2 移植来源三实证（版权头识别法）[🟡 P2]
| 移植物 | 来源 | 证据 |
|--------|------|------|
| Base64 | Josh Bloch / Preferences | 作者头（confucius KP-35） |
| ThreadSafe 注解 | JCIP（Brian Goetz） | 版权头 2005（KP-103） |
| JSON 库 | Android OpenJDK | "Copyright (C) 2010 Android"（KP-145） |

> **方法论沉淀**：移植代码保留原作者版权头——**识别来源的实证手段**（写"是 XX 移植"前必须查 header）

---

## 六、事件/IO/类加载策略补充维度（深度 review 补提取）[工程问题]

### 6.1 事件系统（EventDispatcher 族）[🔴 P1] [时间无关模式]
- **来源**：KP-120a（io.microsphere.event 10 文件全族）
- **机制**：**分发策略化**（EventDispatcher extends Listenable :55 + dispatch :86 + **getExecutor 默认直调** :95——顺序默认）+ **ParallelEventDispatcher**（:33——**ForkJoinPool.commonPool 并行分发** :27——**03 仓库 parallel 主题的源头**）+ DirectEventDispatcher + **按事件类型分派缓存**（AbstractEventDispatcher :50——注册时类型索引）+ ConditionalEventListener 条件监听 + Event/GenericEvent 泛型载体
- **对比**：Spring ApplicationEventMulticaster 的轻量 JDK 版（生态中立）
- **my-xhs**：该用没用——Spring 事件覆盖；分发策略模式可借鉴

### 6.2 IO 序列化与文件监听 [🔴 P2] [时间无关模式]
- **来源**：KP-120b（io 族）
- **机制**：**序列化 SPI**（Serializer\<S> :40——线程安全约束 :24 + **Serializers SPI 加载 + 优先级选优** :36——SPI 注册中心模式在 IO 域复用）+ **FastByteArray 流**（无 synchronized 字节流——性能取舍）+ **FileWatchService**（:54——JDK WatchService 封装——文件/目录变更监听）
- **my-xhs**：已用（Jackson 覆盖序列化）/ Fast 字节流模式可借鉴

### 6.3 classloading/filter/concurrent 族 [🟡 P2] [时间无关模式]
- **来源**：KP-120c
- **机制**：**URLClassPathHandle 版本分派策略族**（:30——implements Prioritized :35 + supports :50——Classic/Modern/NoOp/ServiceLoading 五实现——**按 JDK 版本选择实现**（实现类分派 vs MethodHandle API 探测——版本适配两种模式对照））；**Filter 泛型族**（ClassFilter/JarEntryFilter extends Filter\<T>——Predicate 语义领域化）；**ExecutorUtils**（shutdownOnExit :63——退出钩子关线程池）
- **my-xhs**：不该用——URLClassPath 内部 API 无场景；Predicate 覆盖

## 覆盖核对（53/53）

| 维度 | 覆盖 KP | 数 |
|------|---------|:--:|
| 一、SPI 生态模式 | 123,141,144 + confucius 32 | 3 |
| 二、注解与编译期 | 101,102,103,104,105,109,144,146a,146b,146c | 10 |
| 三、泛型与反射 | 121,122,125,126,127,128,129 | 7 |
| 四、集合与工具 | 105,106,107,108,110,111,112,113,114,115,116,117,118,119,120 | 15 |
| 五、事件与日志 | 136,137,138,139 | 4 |
| 六、版本与兼容 | 132,133 | 2 |
| 七、类加载与构件 | 130,131,134,135,140,141,142,143,145a,145b,145c | 11 |
| 八、转换方向族 | 124a,124b,124c,124d | 4 |
| 九、演进与移植 | 跨仓库汇总 | — |

**去重后唯一 KP**：101-146（含 124a-d/145a-c/146a-c 拆分）全部 = **53/53 ✓**
**无孤儿 KP** ✓（KP-106/107/108 属性解析归四；KP-131 Assert 归七；KP-135 归七；KP-124a-d 归八）
