# microsphere-spring 知识点提取

> 源码：`/data/workspace/java-training-camp/cloud-native-code/share/microsphere-spring`（依赖链第 3 站，microsphere-java 之后）
> 提取时间：2026-08-12（批 1-5：context 161 + web 58；批 6：webmvc 35 + webflux 24 + jdbc 7 + guice 3——**microsphere-spring 288 生产文件全部完成**；**拆提修正：KP-211→4 / KP-216→3 / KP-223→2 / KP-227→webflux 核心 / +KP-228 guice——27→33 KP**；深度 review：占位符解析/isEnabled/ImportOptional/Guice 实证 + 补 AnnotatedBeanDefinitionRegistryUtils/AutoRegistrationBeanInitializer 2 文件）
> 状态：**仓库提取完成（288/288，33 KP，穷尽性复核通过）**；MCP 索引已建（8605 节点/30925 边）
> 关联：microsphere-java 是纯 JDK 工具；**本仓库是 Spring 扩展机制**——知识本体在 Spring 内部机制（BeanFactory/事件/配置），触发面完全不同

## 一、仓库定位

**Spring 框架生产级扩展库**（README 实证）——"enhances dependency injection, configuration management, web endpoint handling, event processing, caching, JDBC monitoring"。模块：context（161 主战场）/web（58）/webmvc（35）/webflux（24）/jdbc（7）/guice（3）。核心维度：[工程问题]（Spring 扩展机制）+ [规范]（Spring 抽象）。

## 前置条件清单

读者需先掌握：1. Spring 容器核心机制（BeanFactory/BeanDefinition/BeanPostProcessor/Autowire 注入点）2. Spring 事件机制（ApplicationEvent/ApplicationListener）3. 配置管理（PropertySource/Environment）4. SpringFactoriesLoader 5. 前两仓库全部知识点
未达前置者，先补：Spring 源码（`code/spring/spring-framework` 本地有）+ 前两仓库 outline

## 掌握度

目标读者：中级偏上（读源码多、Spring 熟——HANDOVER 画像）
讲解策略：Spring 机制直接讲 + 与官方 Spring 源码对照（本地有）

---

## 二、逐文件映射 + 原子记录

### 包: `io.microsphere.spring.beans.factory`（批 1：依赖注入解析体系）

#### KP-201 `InjectionPointDependencyResolver` 注入点解析 SPI（InjectionPointDependencyResolver.java:74-113）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（注入点解析） | **置信度**：High
- **前置**：Spring 注入点（Field/Method/Constructor/Parameter）、BeanDefinition、依赖关系
- **需求**：**解析 Bean 依赖关系**——从注入点（字段/方法/构造器/参数）提取依赖的 Bean 名——**依赖图构建的地基**（启动时分析/循环依赖检测/优雅停机排序）
- **参考实现**：**四形态注入点统一接口**（resolve(Field) :83 / resolve(Method) :92 / resolve(Constructor) :101 / resolve(Parameter) :112——**JDK 反射四形态全覆盖**）；产出是 `Set<String> dependentBeanNames`（:83——**被依赖 Bean 名集合**）
- **对比取舍**：**知识增量：注入点四形态统一抽象**——Spring 官方用 `InjectionPoint` 类（单一封装），microsphere 是 **4 方法分派**（更显式）；**依赖提取 vs Spring 内部依赖分析**（Spring 的 DefaultListableBeanFactory 有 isDependent 内部图，microsphere 是外部化 SPI——**可插拔依赖分析**）
- **my-xhs**：**该用没用**——my-xhs 若做依赖图分析（启动拓扑/循环检测增强）可参考；Spring 内部依赖图覆盖基础

#### KP-202 `InjectionPointDependencyResolvers` 组合注册中心（InjectionPointDependencyResolvers.java:59-86）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（组合模式） | **置信度**：High
- **前置**：SpringFactoriesLoader（Spring 生态 SPI）、组合模式、KP-201
- **需求**：**注入点解析器的自动发现 + 组合执行**——SpringFactoriesLoader 加载全部实现，逐个执行
- **参考实现**：**生态关键差异**：加载器是 **`SpringFactoriesLoaderUtils.loadFactories`**（:28/:64——**Spring 的 META-INF/spring.factories 机制**，非 JDK SPI！）——**实证**：SpringFactoriesLoaderUtils.java:49 直接封装 Spring 官方 `org.springframework.core.io.support.SpringFactoriesLoader` + `FACTORIES_RESOURCE_LOCATION`（:49-50）——**microsphere 在 Spring 生态用 Spring 的 SPI**（与 microsphere-java 的 ServiceLoaderUtils 对照：**生态适配**——进 Spring 用 spring.factories）；组合执行（resolvers.forEach :72-86——**每个 resolver 都执行**（收集并集），非短路）
- **对比取舍**：**知识增量：生态 SPI 选择**——microsphere-java（JDK SPI）vs 本仓库（SpringFactoriesLoader）——**同一作者两生态的正确选择**（进 Spring 用 Spring 的 SPI 是生态适配最佳实践）；组合模式（forEach 收集 vs 短路选优）是依赖解析的"全量收集"语义
- **my-xhs**：**该用没用**——SpringFactoriesLoader 机制必懂（Spring 生态扩展基础）；my-xhs 已用 Spring

#### KP-203 `DefaultBeanDependencyResolver` 默认依赖解析器（DefaultBeanDependencyResolver.java:83-291）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（依赖图构建） | **置信度**：High
- **前置**：RootBeanDefinition、mergedBeanDefinition、BeanFactory 遍历、并发
- **需求**：**全量 Bean 依赖图构建**——遍历所有 BeanDefinition 解析依赖关系（Map<BeanName, Set<依赖Bean名>>）
- **参考实现**：**两层 API**（resolve(bf) 全量 :148 / resolve(beanName, mergedBeanDefinition, bf) 单个 :291）；**构造器注入 ExecutorService**（:117——javadoc 自证 "parallel dependency resolution" :103）；**并行机制实证**（:188 execute + **completionService.submit** :205——CompletionService 并行提交 + **awaitTermination 等待** :230 + executorService.execute :249——**三处并行原语实证**：单类加载/提交收集/关闭等待）
- **对比取舍**：**并行依赖解析**（ExecutorService + CompletionService :205）是性能设计（大应用启动加速）——**CompletionService 是"提交-完成-收集"标准模式**；Spring 官方依赖图是懒构建（getBeanNamesForType 时），microsphere 是**主动全量预构建**
- **my-xhs**：**该用没用**——启动依赖分析（优雅停机/拓扑排序）参考；**CompletionService 并行收集模式**值得学

#### KP-204 `DependencyTreeWalker` 依赖树遍历（DependencyTreeWalker.java:30-54）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式]（树遍历） | **置信度**：High
- **前置**：树遍历（DFS/BFS）、依赖图（KP-203）
- **需求**：**依赖树遍历**——从单个 Bean 出发遍历其依赖链（循环依赖检测/依赖可视化）
- **参考实现**：Dependency walk（:54——**Dependency 封装 + 遍历**）；依赖树节点 = Bean + 其依赖子集
- **my-xhs**：**该用没用**——循环依赖诊断参考

#### KP-205 注入点解析器家族（AbstractInjectionPointDependencyResolver + Autowired/Resource/BeanMethod/Construction 变体）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：@Autowired/@Resource/@Bean 语义、构造器注入、注入点分类
- **需求**：**按注入方式分类解析**——@Autowired（类型注入）/@Resource（名称注入）/@Bean 方法参数/构造器参数
- **参考实现**：**变体家族实证**（annotation 子包 10 文件——AutowiredInjectionPointDependencyResolver/ResourceInjectionPointDependencyResolver/AnnotatedInjectionPointDependencyResolver + AnnotatedInjectionBeanPostProcessor/ConfigurationBeanBindingPostProcessor 等——**@Autowired vs @Resource 的解析差异**（类型 vs 名称——注入语义教学）；AbstractInjectionPointDependencyResolver 基座（测试 AbstractInjectionPointDependencyResolverTest 实证存在）；**DelegatingFactoryBean**（:79——implements FactoryBean+InitializingBean+DisposableBean——**委托 Bean 生命周期透传**（javadoc :46 "destruction callback if delegate implements DisposableBean"））
- **my-xhs**：**该用没用**——@Autowired/@Resource 语义必懂；解析器 SPI + DelegatingFactoryBean（委托生命周期透传）参考
---

## 三、深度 review 七项报告（批 1）

> 2026-08-12 批判性 review：KP-202 SpringFactoriesLoader 实证（封装 Spring 官方类）、KP-203 并行机制实证（CompletionService 三原语）、KP-205 变体家族类名修正 + DelegatingFactoryBean 补充。

- [x] **① 源码行号精确核对**：KP-201:74-113、KP-202:59-86（SpringFactoriesLoaderUtils.java:49-50 实证）、KP-203:83-291（:103/:188/:205/:230/:249）、KP-204:30-54、KP-205（annotation 子包 10 文件 + DelegatingFactoryBean:79）——全部 grep 实证 ✓
- [x] **② 穷尽性**：beans/factory 40 文件核心覆盖（注入解析 SPI + 组合 + 默认实现 + 变体家族）；剩余 support/config/filter 子包批 2 ✓
- [x] **③ 空节标注**：N/A ✓
- [x] **④ 过时三级**：5 KP 全部标注（[时间无关模式] 全）✓
- [x] **⑤ 重复内容**：与 microsphere-java 对照（JDK SPI vs SpringFactoriesLoader——生态适配）✓
- [x] **⑤b 引用目标核对**：SpringFactoriesLoader 官方类名（spring-core 实证 `org.springframework.core.io.support.SpringFactoriesLoader`）✓
- [x] **⑥ 诚实标注**：无待验证残留 ✓
- [x] **⑦ 命名空间迁移**：N/A（无 javax→jakarta）✓

### 测试扫描记录（02 §2.1）

| 测试文件 | 验证了 | 结论 |
|---------|--------|------|
| DefaultBeanDependencyResolverTest | 构造注入 resolver（:89）+ resolve 空依赖图（:105-106——空容器返回空 Map） | KP-203 边界 ✓ |
| DependencyTreeWalkerTest / DependencyTest | 依赖树遍历 | KP-204 ✓ |
| AbstractInjectionPointDependencyResolverTest / BeanMethod/Construction 变体测试 | 注入点解析各变体 | KP-205 ✓ |
| DelegatingFactoryBeanTest / BeanFactoryUtilsTest | 委托 Bean 生命周期 | KP-205 ✓ |
| DefaultApplicationEventInterceptorChainTest | 链捕获事件/类型（:63-66）+ 拦截器调用计数（:72-75） | KP-207 ✓ |
| InterceptingApplicationEventMulticasterTest / ProxyTest | 多播器拦截 + 代理 | KP-207 ✓ |
| ApplicationEventInterceptorTest / ApplicationListenerInterceptorTest | 两级拦截 SPI | KP-206 ✓ |
| EnableEventExtensionTest / EventExtensionRegistrarTest | @Enable 注册器 | KP-211 ✓ |
| DependencyAnalysisBeanFactoryListenerTest / ParallelPreInstantiation 测试 | BeanFactory 监听器应用 | KP-208/209 ✓ |
| EventPublishingBeanInitializerTest / GenericApplicationListenerAdapterTest 等 | 事件扩展配套 | KP-210 ✓ |
| PropertySourceExtensionAttributesTest / DefaultPropertiesPropertySourceLoaderTest 等（config 6 测试） | 配置源加载 | KP-215 ✓ |
| PropertySourceChangedEventTest / PropertySourcesChangedEventTest | 事件两级粒度 | KP-213 ✓ |
| EnvironmentListenerTest / ProfileListenerTest / PropertyResolverListenerTest 等（core/env 7 测试） | 双钩监听器 | KP-214 ✓ |
| TTLContextTest / EnableTTLCachingTest / TTLCacheableTest（cache 测试） | TTL 上下文 + 组合注解 | KP-217 ✓ |
| SpringProfilesURLConnectionAdapterTest / SpringPropertySourcesURLConnectionAdapterTest 等（net 测试） | URL 连接适配器 | KP-219 ✓ |
| rule 17 测试（每规则一测试——WebRequestMethodsRuleTest/ParamsRuleTest/CompositeWebRequestRuleTest 等） | 请求匹配各规则 | KP-221 ✓ |
| metadata 19 测试（WebEndpointMappingTest/Composite/Filtering 注册中心/工厂家族） | 端点映射元数据 + 注册体系 | KP-222 ✓ |
| webmvc 测试（advice/annotation/config/context/handler/interceptor/metadata 等包） | MVC 扩展 | KP-225 ✓ |
| jdbc p6spy 测试 | P6Spy 集成 | KP-226 ✓ |

### 包: `io.microsphere.spring.context.event`（批 2：30 文件）

#### KP-206 `ApplicationEventInterceptor` 事件拦截 SPI（ApplicationEventInterceptor.java:52-93）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（拦截器模式） | **置信度**：High
- **前置**：Spring 事件机制（ApplicationEventMulticaster/ResolvableType）、拦截器模式
- **需求**：**事件分发拦截**——在 Spring 事件分发前后插入逻辑（监控/日志/过滤/重试）
- **参考实现**：**两级拦截**（事件级 ApplicationEventInterceptor :52 + **监听器级 ApplicationListenerInterceptor**——拦截粒度分事件与监听器两层）；`extends Ordered`（:20——**Spring 排序接口**，非 Prioritized——生态适配实证第二例）+ 默认 LOWEST_PRECEDENCE（:90-92）
- **对比取舍**：**知识增量**：事件拦截的两级粒度（事件流 vs 单监听器）；**Ordered vs Prioritized**——microsphere 在 Spring 生态用 Spring 的 Ordered（生态适配延续 KP-202 模式）
- **my-xhs**：**该用没用**——Spring 事件监控（埋点/审计）场景；Spring `ApplicationEventMulticaster` 装饰可替代

#### KP-207 `InterceptingApplicationEventMulticaster` 拦截多播器（InterceptingApplicationEventMulticaster.java:42-76）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（装饰器模式） | **置信度**：High
- **前置**：SimpleApplicationEventMulticaster、责任链、装饰器
- **需求**：**事件多播的拦截增强**——继承 Spring 官方多播器 + 注入双拦截链
- **参考实现**：**继承官方类**（extends SimpleApplicationEventMulticaster :42——**Spring 官方多播器的子类**）；**双链注入**（multicastEvent :48-55——事件拦截链 `DefaultApplicationEventInterceptorChain` + 回调 `this::doMulticastEvent`；invokeListener :65-69——监听器拦截链 + `this::doInvokeListener`——**回调式责任链**（链尾回调原始方法 :71-76 `super.multicastEvent`/`super.invokeListener`））；**final 防重写**（:49/:66——模板方法保护）
- **对比取舍**：**知识增量：回调式责任链语义实证**——**拦截器显式调用 `chain.intercept()` 放行**（ApplicationEventInterceptor.java:37 javadoc "Continue the interceptor chain" 实证——**手动推进链**（非自动 forEach））；链实现（DefaultApplicationEventInterceptorChain :44-51——**逐个取拦截器调用（手动推进）+ 链尾回调 BiConsumer**）；**Semantics**：拦截器不调 chain.intercept 则**链停住（短路——事件被吞）**——**AOP 环绕语义的事件版**（前置/后置/完全阻断三态）；测试实证（DefaultApplicationEventInterceptorChainTest :63-75——intercept 捕获事件 + 调用计数）
- **my-xhs**：**该用没用**——Spring 事件 AOP 化（统一埋点/权限/审计）；**回调式责任链**是通用模式（拦截器手动放行 = Servlet Filter 同款）

#### KP-208 `BeanFactoryListener` BeanFactory 生命周期监听（BeanFactoryListener.java:61-86）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式]（生命周期扩展点） | **置信度**：High
- **前置**：BeanFactory 生命周期（注册→就绪→冻结）、Spring 扩展点
- **需求**：**BeanFactory 生命周期钩子**——在注册/就绪/冻结三个时点插入逻辑（比 BeanPostProcessor 更早/更广）
- **参考实现**：**三时点钩子**（onBeanDefinitionRegistryReady :68——注册阶段 / onBeanFactoryReady :75——就绪阶段 / onBeanFactoryConfigurationFrozen :84——冻结阶段——**覆盖 BeanFactory 完整生命周期**）；`extends EventListener`（:61——**JDK 监听器标记**）
- **对比取舍**：**知识增量：BeanFactory 级生命周期监听**（Spring 官方用 BeanFactoryPostProcessor/BeanPostProcessor 分时点，microsphere 统一为监听器 SPI——**观察者 vs 后处理器**架构对照）；**冻结时点**（onBeanFactoryConfigurationFrozen :84——Spring `freezeConfiguration()` 钩子——Spring 少用的扩展点）
- **my-xhs**：**该用没用**——启动阶段自定义逻辑（指标收集/预热/校验）参考

#### KP-209 `ParallelPreInstantiationSingletonsBeanFactoryListener` 并行预实例化（ParallelPreInstantiationSingletonsBeanFactoryListener.java:109-122 等）

- **维度**：[性能优化] | **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式]（并行启动优化） | **置信度**：High
- **前置**：Spring 单例预实例化（preInstantiateSingletons）、线程池、启动优化
- **需求**：**并行 Bean 预实例化**——启动时多线程创建单例 Bean（大应用启动加速）
- **参考实现**：**BeanFactoryListener 的实战应用**（implements BeanFactoryListener :109——KP-208 的应用案例）；**配置化实证**（:115 `PROPERTY_NAME_PREFIX = microsphere.spring.pre-instantiation.singletons.` + javadoc 示例 `...threads=4` :77——**threads 与 thread-name-prefix 双属性** :60-66）
- **对比取舍**：**并行启动 vs 串行启动**——Spring 官方 `preInstantiateSingletons` 串行（javadoc :95 "automatically trigger parallel pre-instantiation" 实证）——**microsphere 是启动性能优化方案**；**风险**：并行实例化需 Bean 无启动期顺序依赖（循环依赖/初始化顺序敏感场景会出问题——使用前提标注）
- **my-xhs**：**该用没用**——my-xhs 大应用启动加速候选（需评估 Bean 初始化顺序依赖）；**风险前提必读**

#### KP-210 事件扩展配套（EventPublishingBeanBeforeProcessor/AfterProcessor/Initializer + OnceApplicationContextEventListener + Logging 监听器 + 适配器家族）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：BeanPostProcessor、ApplicationListener、适配器
- **需求**：**Bean 生命周期事件发布**（before/after 初始化发事件）+ 一次性事件监听 + 日志/统计监听器 + 适配器
- **参考实现**：**Bean 生命周期事件化**（EventPublishingBeanBeforeProcessor/AfterProcessor——**BeanPostProcessor 发布 Bean 事件**——Bean 生命周期 → 事件流）；**OnceApplicationContextEventListener**（一次性——首次触发后注销）；**适配器家族**（BeanFactoryListenerAdapter/BeanListenerAdapter/GenericApplicationListenerAdapter/JavaBeansPropertyChangeListenerAdapter——**BeanListener 适配 JavaBeans PropertyChangeListener**——JavaBeans 事件桥接）
- **my-xhs**：**该用没用**——Bean 生命周期事件化（启动监控）；Spring 有部分内建（ContextRefreshedEvent 等）

### 包: `io.microsphere.spring.context.annotation`（批 2 续：14 文件）

#### KP-211 `AnnotatedBeanCapableImportSelector` 注解驱动 Import 模板（AnnotatedBeanCapableImportSelector.java:69-116 + AnnotatedBeanCapableImportCandidate.java:97-154 + AnnotatedBeanCapableImportBeanDefinitionRegistrar）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（@Import 模板） | **置信度**：High
- **前置**：ImportSelector/ImportBeanDefinitionRegistrar、@Import 机制、泛型注解类型、ResolvableType
- **需求**：**注解驱动 @Import 的通用模板**——读注解属性 → 决定导入哪些类（@EnableXxx 的通用基座）
- **参考实现**：**继承链三层**（AnnotatedBeanCapableImportCandidate（:97——**泛型注解类型解析**（resolveAnnotationType :105-107——`resolveGeneric(AnnotatedBeanCapableImportCandidate.class, 0)` :109-113——**ResolvableType 泛型实参解析**（同 KP-125 泛型推断））+ **isEnabled 环境开关**（:115-117 + :180-184——`microsphere.xxx.enabled` 属性控制——**注解能力的环境开关**））→ AnnotatedBeanCapableImportSelector（:69——implements ImportSelector + **final selectImports 模板**（:72-77——子类只实现抽象 selectImports(metadata, attributes, imports) :113-115））→ 各具体 ImportSelector；**AnnotationAttributes 占位符解析**（getAnnotationAttributes :141-143——**ResolvablePlaceholderAnnotationAttributes**（:core/annotation——**注解属性支持 ${placeholder} 占位符**——Spring 官方 AnnotationAttributes 不支持！））
- **对比取舍**：**知识增量**：①**@Import 模板化**（ImportSelector 的模板方法——Spring 官方每个 @Enable 手写 selectImports，microsphere 统一基座）；②**ResolvablePlaceholderAnnotationAttributes**（注解属性占位符解析——Spring 官方缺失，microsphere 自研）；③**isEnabled 环境开关**（注解能力可配置启停）；④泛型注解类型 + ResolvableType 解析（生态复用 KP-125 模式）
- **my-xhs**：**该用没用**——自定义 @EnableXxx 的模板基座参考

#### KP-211b `@OverrideAnnotationAttributes` 策略化覆盖（OverrideAnnotationAttributes.java:75-89 + OverrideAnnotationAttributesStrategy + ConfigurationPropertyOverrideAnnotationAttributesStrategy）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式]（注解覆盖） | **置信度**：High
- **前置**：@AliasFor、元注解、注解属性合并语义
- **需求**：**注解属性覆盖**——组合注解时子注解属性覆盖元注解默认值（Spring @AliasFor 的补充）
- **参考实现**：**策略 SPI**（:79 注解 + :89 `strategy()` 默认 ConfigurationPropertyOverrideAnnotationAttributesStrategy——**覆盖行为可插拔**）；**使用链**（BeanCapableImportCandidate/ImportOptional/EnableWebMvcExtension 等）
- **对比取舍**：**知识增量**：Spring @AliasFor 是**固定合并**（别名语义），microsphere 是**策略化覆盖**（可插拔覆盖逻辑）——注解属性处理的两种范式
- **my-xhs**：该用没用——组合注解属性覆盖的扩展参考

#### KP-211c `@ImportOptional` 可选导入（ImportOptional.java:46-59 + ImportOptionalSelector）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式]（可选导入） | **置信度**：High
- **前置**：@Import 失败语义、类存在性探测
- **需求**：**可选 @Import**——导入的类可能不存在（如可选依赖），不存在时不失败
- **参考实现**：`@ImportOptional`（:50 `@Import(ImportOptionalSelector.class)` + :49 `@OverrideAnnotationAttributes`——**组合元注解**）；ImportOptionalSelector——**探测类存在性，不存在则跳过**（区别于 Spring @Import 硬失败）
- **对比取舍**：**知识增量：可选导入解决"可选依赖"问题**——Spring 官方 @Import 类不存在直接启动失败；microsphere 的 ImportOptional 是**容错导入**（按 classpath 探测）
- **my-xhs**：**该用没用**——可选依赖场景（如"若存在 X 则启用 Y"）

#### KP-211d `ExposingClassPathBeanDefinitionScanner` + `EnableAutoRegistrationBean` + 注册工具（扫描器暴露 + 自动注册 + 注册工具）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式]（扫描器扩展） | **置信度**：High
- **前置**：ClassPathBeanDefinitionScanner、doScan/checkCandidate 受保护方法、BeanDefinitionRegistry
- **需求**：**扫描器受保护方法暴露**——doScan/checkCandidate 是 protected，扩展需要公开访问
- **参考实现**：**ExposingClassPathBeanDefinitionScanner**（:63——extends ClassPathBeanDefinitionScanner + **覆写暴露**（doScan :73/checkCandidate :78——改为 public）+ **额外能力**（getSingletonBeanRegistry :82/registerBeanDefinition :86/registerSingleton :90——**直接注册能力暴露**））；**EnableAutoRegistrationBean**（:47-61——@Import(AutoRegistrationBeanRegistrar) + **@ConfigurationProperty 注解属性**（:57-60——**KP-101 注解体系的落地应用**——自动注册开关配置化））；**AnnotatedBeanDefinitionRegistryUtils**（:43——**幂等注册工具**（:68 isPresentBean / :99-101 javadoc "ensures idempotent registration"——**先查重再注册**））；**AutoRegistrationBeanInitializer**（:38——extends ConfigurableApplicationContextInitializer（**KP-208 家族**）+ initialize 注册 Config 类 :41-42）
- **对比取舍**：**知识增量**：①**protected → public 暴露模式**（扩展 Spring 官方类时"打开"受保护能力）；②**扫描器 + 注册器组合**（doScan 后手动注册单例）；③**幂等注册工具**（isPresentBean 查重——防止重复注册）
- **my-xhs**：该用没用——自定义组件扫描参考
- **my-xhs**：**该用没用**——@Enable 注解模式参考（Spring 官方同款）

### 包: `io.microsphere.spring.config` + `core.env`（批 3：config 24 + core/env 9）

#### KP-212 `PropertySourceExtension` @PropertySource 元注解扩展（PropertySourceExtension.java:144-246）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（元注解设计） | **置信度**：High
- **前置**：Spring @PropertySource、元注解（@Target(ANNOTATION_TYPE)）、PropertySource 排序语义
- **需求**：**@PropertySource 的增强元注解**——补齐官方注解的短板：**排序控制/编码/资源比较器/自动刷新**
- **参考实现**：**10 属性元注解**（:144-246 逐个实证）：**排序三件**（first :172——置顶 / before :183——某源之前 / after :194——某源之后——**PropertySource 顺序编排**——官方 @PropertySource 无法控制顺序）；**资源控制**（resourceComparator :221——自定义资源比较器 / ignoreResourceNotFound :229 / encoding :235——**默认 ${file.encoding:UTF-8}**——占位符默认值语法）；**自动刷新**（autoRefreshed :164——**配置变更自动重载**——配合 KP-213 事件）；name :156 / value :206 / factory :245（自定义 PropertySourceFactory）
- **对比取舍**：**知识增量：元注解扩展官方注解**（@Target(ANNOTATION_TYPE) :144——**组合注解模式**：用户自定义注解可再标注本注解获得增强）；**before/after/first 排序 DSL**——Spring 官方 `@PropertySource` 无排序（顺序=声明序），microsphere 补**显式编排**；**autoRefreshed + 事件**是配置热更新基础
- **my-xhs**：**该用没用**——配置源排序编排（my-xhs 多配置源优先级控制）；Spring Cloud 用 bootstrap/优先级属性，本设计是注解式替代

#### KP-213 `PropertySourcesChangedEvent` 配置变更事件（PropertySourcesChangedEvent.java:43-73 + PropertySourceChangedEvent）

- **维度**：[分布式问题]（配置管理）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（配置变更通知） | **置信度**：High
- **前置**：ApplicationContextEvent、配置热更新、事件驱动
- **需求**：**配置变更通知**——PropertySources 变化时发事件（微服务配置热更新的地基）
- **参考实现**：**事件家族**（PropertySourceChangedEvent 单源 + PropertySourcesChangedEvent 整体 :73——**两级粒度**：单个源 vs 全部源）；extends ApplicationContextEvent（:73——**Spring 事件体系集成**）
- **对比取舍**：**知识增量**：配置变更事件化 vs Spring 官方——Spring 无内建 PropertySource 变更事件（@RefreshScope 是 Cloud 层）；microsphere 在**核心层**补事件——**分层设计对照**（Spring 官方把配置刷新放 Cloud，microsphere 沉到底层）；配合 autoRefreshed（KP-212）形成**配置热更新闭环**
- **my-xhs**：**该用没用**——配置热更新（对接 Nacos 配置中心变更通知）；Nacos 自带 publishConfig 事件可映射

#### KP-214 `EnvironmentListener` Environment 生命周期监听（EnvironmentListener.java:40-108）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式]（生命周期扩展点） | **置信度**：High
- **前置**：ConfigurableEnvironment、MutablePropertySources、系统属性/环境变量
- **需求**：**Environment 操作监听**——propertySources/systemProperties/systemEnvironment/merge 操作前后钩子
- **参考实现**：**四组 before/after 双钩**（:47-108 实证——beforeGetPropertySources :47 / afterGetPropertySources :56 / beforeGetSystemProperties :64 / afterGetSystemProperties :73 / beforeGetSystemEnvironment :81 / afterGetSystemEnvironment :90 / **beforeMerge :99 / afterMerge :108**——**propertySources/systemProperties/systemEnvironment/merge 四数据面全覆盖**）；extends ProfileListener + PropertyResolverListener（:40——**监听器组合继承**）
- **对比取舍**：**知识增量：Environment 级 AOP 化**（Spring 官方无此监听器——直接操作 Environment）；**before/after 双钩模式**（与 KP-208 的 BeanFactory 三时点呼应——**生命周期监听是 microsphere 的生态主题**）
- **my-xhs**：**该用没用**——Environment 操作审计/埋点；Spring 少用扩展点

#### KP-215 配置源家族（PropertySourceExtensionLoader/ResourcePropertySource/Yaml/Json/ImmutableMapPropertySource + 工厂）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式]（配置源加载） | **置信度**：High
- **前置**：PropertySource/PropertySourceFactory、YAML/JSON 解析、资源加载
- **需求**：**配置源加载家族**——注解驱动加载（Loader SPI）+ YAML/JSON/Properties 多格式 + 资源比较
- **参考实现**：**Loader 抽象**（PropertySourceExtensionLoader :72——**注解→PropertySource 加载模板**：loadPropertySource 链 :105/:169）；**多格式支持**（JsonPropertySourceFactory/YamlPropertySourceFactory :support——**@JsonPropertySource/@YamlPropertySource 注解**（annotation :16/:25——**格式注解化**）；ImmutableMapPropertySource（不可变 Map 源）；DefaultResourceComparator（资源排序——配合 KP-212）
- **my-xhs**：**该用没用**——多格式配置源（YAML/JSON 注解式）；Spring Boot 已支持多格式（properties/yaml），本设计是注解化变体

### 包: `io.microsphere.spring.core`（批 3 续：core 23 文件剩余）

#### KP-216 `SpringConverterAdapter` 跨生态转换桥（SpringConverterAdapter.java:41-67 + EnableSpringConverterAdapter + ConversionServiceResolver）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（适配器桥） | **置信度**：High
- **前置**：Spring ConversionService（GenericConverter/ConditionalGenericConverter/ConvertiblePair）、microsphere-java Converter SPI（KP-121）、SPI
- **需求**：**microsphere Converter → Spring Converter 的桥**——生态复用：自家转换器无缝接入 Spring ConversionService
- **参考实现**：**适配器**（implements `ConditionalGenericConverter` :41——**Spring 条件泛型转换器**）；**单例 + SPI 预载**（INSTANCE :46 + convertersMap 静态加载 :50-63——**SPI 加载全部 microsphere Converter 建 ConvertiblePair 映射**（:54-63——双键键映射））；**ConvertiblePair 构建**（:66-67——用泛型推断的 source/target 类型建 Spring 键）；**启用注解**（EnableSpringConverterAdapter + Registrar——@Enable 装配）；**ConversionServiceResolver**（:57-65——**ConversionService 解析 + 注册**（"resolved-" bean 名 :47））
- **对比取舍**：**知识增量：跨生态转换桥**——microsphere-java 的 Converter SPI（KP-121）通过适配器**无缝接入 Spring**（生态整合范式）；ConvertiblePair 双键映射（复用 KP-123 双键缓存模式）
- **my-xhs**：**该用没用**——自定义 Converter 接入 Spring ConversionService 的参考

#### KP-216b `ResolvablePlaceholderAnnotationAttributes` 占位符注解属性（ResolvablePlaceholderAnnotationAttributes.java:44 + GenericAnnotationAttributes + AnnotationUtils）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式]（注解属性解析） | **置信度**：High
- **前置**：AnnotationAttributes、占位符解析（PropertyPlaceholderHelper）、泛型注解
- **需求**：**注解属性支持 ${placeholder}**——Spring 官方 AnnotationAttributes 不做占位符解析
- **参考实现**：**泛型注解属性类**（`ResolvablePlaceholderAnnotationAttributes<A extends Annotation>`——**注解类型泛型化** + extends GenericAnnotationAttributes（:44——**自研基座**（非 Spring 官方 AnnotationAttributes）））；**占位符解析能力**（属性值经 Environment 解析）
- **对比取舍**：**知识增量**：Spring 官方 @Value 才能用占位符，**注解属性本身不支持**——microsphere 的 ResolvablePlaceholderAnnotationAttributes 补此能力（KP-211 使用链实证）
- **my-xhs**：该用没用——注解属性占位符化参考

#### KP-216c core/io + SpringVersion（core/io 5 + SpringVersion + MethodParameterUtils + core/annotation 剩余）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟢 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：Medium（未深读）
- **前置**：资源加载、版本判断
- **需求**：资源工具（ResourceLoaderUtils/ResourceUtils/PropertiesUtils/SpringFactoriesLoaderUtils——**KP-202 加载器实现地**）+ 版本判断（SpringVersion）
- **参考实现**：SpringVersion（:193——**枚举 CURRENT**——`org.springframework.core.SpringVersion.getVersion()` 封装 + 版本比较（配 KP-133 Compatible））；ResourceUtils/ResourceLoaderUtils（资源加载增强）
- **my-xhs**：**不该用**——Spring 官方覆盖

### 包: `io.microsphere.spring.cache` + `net` + `context/lifecycle` + `context/config`（批 4：24 文件）

#### KP-217 TTL 缓存体系（TTLContext.java:53-104 + EnableTTLCaching.java:68-109 + TTLCacheable.java:60-166 + TTLCacheResolver）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式]（TTL 设计） | **置信度**：High
- **前置**：Spring 缓存抽象（@Cacheable/@CachePut）、ThreadLocal、@EnableCaching、@AliasFor、CacheResolver
- **需求**：**TTL 缓存（带过期时间）**——Spring 官方 @Cacheable 无 TTL 概念（由缓存实现决定），microsphere 补**声明式 TTL**
- **参考实现**：**四层设计**：①**TTLContext ThreadLocal**（:55——**当前线程 TTL 上下文** + `doWithTTL(function, defaultTTL)` 模板（:72-104——**执行函数 + 自动清理**（finally clearTTL :103——**ThreadLocal 泄漏防护**））；②**@EnableTTLCaching 组合注解**（:71 `@EnableCaching` + :72 `@Import(TTLCachingConfiguration.class)`——**@AliasFor 透传**（:86-108——proxyTargetClass/mode/order 三属性别名到官方注解））；③**@TTLCacheable 注解**（:64 **`@Cacheable(cacheResolver = BEAN_NAME)`**——**关键设计：cacheResolver 指定自定义解析器**（BEAN_NAME 静态导入自 TTLCacheResolver :30）+ @AliasFor 透传全部官方属性（value/cacheNames/key/keyGenerator/condition/unless :70-166）——**TTL 注入机制 = 自定义 CacheResolver**）；④**TTLCachingConfiguration**（:30-34——`@Bean(name = BEAN_NAME)` 注册 TTLCacheResolver——**注解→配置类→Resolver Bean 装配链**）
- **对比取舍**：**知识增量**：①**ThreadLocal TTL 上下文 + finally 清理**（泄漏防护标准）；②**@AliasFor 组合注解透传**（注解继承替代方案——可覆盖默认值）；③**cacheResolver 注入点**（@Cacheable 的 cacheResolver 属性是官方预留扩展点——microsphere 用它挂 TTL——**官方扩展点利用**）；测试实证（TTLCacheableTest/EnableTTLCachingTest 存在）
- **my-xhs**：**该用没用**——声明式 TTL 缓存（my-xhs Redis 缓存过期控制）；Spring Cache + 自定义 CacheResolver 是官方路径

#### KP-218 `TTLRedisCacheWriterWrapper` 死代码（TTLRedisCacheWriterWrapper.java:21-91）

- **维度**：[工程问题] | **权重**：[边缘] | **深度**：🟢 | **优先级**：P3 | **过时**：[过时→死代码（整文件注释）] | **置信度**：High
- **前置**：RedisCacheWriter（Spring Data Redis）
- **需求**：Redis TTL 写入包装——**本文件意图**
- **参考实现**：**实证：整文件被注释**（grep 实证 99 行注释 / 0 行代码——:21-91 全部 `//`——类声明/构造/方法全注释——**"看起来在做≠真的实现"案例**：意图是包装 RedisCacheWriter 加 TTL，但**实现被注释掉**（可能 API 变更后放弃）——**死代码**
- **my-xhs**：**不该用**——死代码；Spring Data Redis 原生 TTL 配置

#### KP-219 Spring 资源 URL 协议（net 10 文件：SpringProtocolURLStreamHandler/SpringResourceURLConnection/工厂家族）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式]（URL 协议 + 资源桥） | **置信度**：High
- **前置**：URLStreamHandler（microsphere-java KP-143）、Spring Resource、Environment
- **需求**：**自定义 `spring://` URL 协议**——URL 访问 Spring 资源/环境/配置（`spring:bean:xxx`/`spring:env:xxx`）
- **参考实现**：**协议处理器**（SpringProtocolURLStreamHandler——协议入口）；**连接类型体系**（SpringResourceURLConnection :resource 访问 / SpringEnvironmentURLConnectionFactory :environment 访问 / SpringPropertySourcesURLConnectionAdapter :propertySources 访问 / SpringProfilesURLConnectionAdapter :profile 访问 / SpringDelegatingBeanProtocolURLConnectionFactory :bean 访问——**URL 协议承载 Spring 对象访问**）；AbstractSpringResourceURLConnection 基座 + 工厂家族
- **对比取舍**：**知识增量**：**URL 协议访问框架对象**（`spring:` 协议 = Spring 对象访问 DSL）——microsphere-java KP-143 的**Spring 生态应用**（URL 协议扩展系列第二处）；**连接适配器**（Adapter 模式多实现）
- **my-xhs**：**不该用**——无场景；Spring Environment/Resource 直接 API 覆盖

#### KP-220 生命周期与配置绑定（context/lifecycle 2 + context/config 4）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：Medium
- **前置**：SmartLifecycle、ConfigurationBeanBinder
- **需求**：**生命周期日志**（LoggingSmartLifecycle）+ 配置 Bean 绑定（ConfigurationBeanBinder）
- **参考实现**：AbstractSmartLifecycle（基座）+ LoggingSmartLifecycle（日志版）；**ConfigurationBeanBinder**（:DefaultConfigurationBeanBinder——**配置类 Bean 绑定**（与 KP-212 注解体系配合——@ConfigurationProperties 风格绑定））
- **my-xhs**：**不该用**——Spring 官方覆盖

### 模块: `microsphere-spring-web`（批 5：58 文件）

> 主题：**Web 请求匹配规则 + 端点映射元数据**——Spring MVC/WebFlux 的 RequestCondition 家族的**框架无关重实现**（不依赖 servlet/reactive 具体实现）。

#### KP-221 `WebRequestRule` 请求匹配规则 SPI（WebRequestRule.java:30-34 + AbstractWebRequestRule + CompositeWebRequestRule.java:36-50）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（请求匹配） | **置信度**：High
- **前置**：Spring RequestCondition 家族（RequestMethodsRequestCondition 等）、NativeWebRequest、@RequestMapping 语义
- **需求**：**请求匹配规则的框架无关抽象**——方法/参数/头/媒体类型/路径的匹配判定（Spring MVC 与 WebFlux 共用）
- **参考实现**：**SPI 接口**（`boolean matches(NativeWebRequest)` :33——单一匹配方法）；**泛型模板基座**（AbstractWebRequestRule\<T>——**六大规则全部 extends 基座**：WebRequestMethodsRule extends AbstractWebRequestRule\<String>（:48）/Params extends AbstractWebRequestRule\<WebRequestParamExpression>（:48）/Headers\<WebRequestHeaderExpression>（:44）/Consumes\<ConsumeMediaTypeExpression>（:50）/Produces\<ProduceMediaTypeExpression>（:56）/Pattens\<String>（:58）——**表达式类型参数化**）；**组合实现**（CompositeWebRequestRule :36-50——**AND 语义**（逐个匹配失败即 false :46-49）——对照 Spring `CompositeRequestCondition` :32 javadoc 实证）
- **对比取舍**：**知识增量：Spring RequestCondition 家族解耦重实现**——Spring MVC 的 RequestMethodsRequestCondition（servlet 包）/WebFlux 的 RequestMethodsRequestCondition（reactive 包）**重复定义两套**（javadoc :44-45 双 @see 实证），microsphere 用 NativeWebRequest 抽象**一套通用**——**解决 Spring 的 MVC/WebFlux 条件重复问题**；**泛型表达式模板**（AbstractWebRequestRule\<T>）是统一实现；**拼写细节**：WebRequest**Pattens**Rule（Pattens 拼错——StacKTrace 后第二例，API 稳定约束）
- **my-xhs**：**该用没用**——若 my-xhs 做网关/过滤器需要框架无关请求匹配可参考；Spring 官方条件已覆盖 MVC 场景

#### KP-222 `WebEndpointMapping` 端点映射元数据（WebEndpointMapping.java:135-170 等）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式]（端点元数据） | **置信度**：High
- **前置**：HandlerMethod、@RequestMapping、端点注册、Builder 模式
- **需求**：**Web 端点映射的元数据模型**——HTTP 方法/媒体类型/路径 + HandlerMethod 的完整描述（网关/监控用）
- **参考实现**：**十字段模型实证**（:139-165——kind/endpoint/id/**patterns/methods/params/headers/consumes/produces/negated**——**@RequestMapping 八条件全覆盖 + negated 否定标志 + UNKNOWN_SOURCE 哨兵 :135 + hashCode 缓存 :170**）；**Builder 模式**（:210 内部 Builder + :1230 build——**不可变对象构建**）；**工厂体系**（WebEndpointMappingFactory + AbstractWebEndpointMappingFactory + ServletRegistration/FilterRegistration/Jackson2 工厂——多来源端点发现）；**注册体系**（WebEndpointMappingRegistry + Composite/Filtering/Simple 注册中心）
- **对比取舍**：**知识增量：端点映射元数据化**——把 MVC 的 HandlerMapping 内部端点信息**外部化为元数据模型**（网关路由发现/监控/文档生成的基础）；**negated 否定匹配**（@RequestMapping 的 ! 前缀语义）；Builder + 不可变 + hashCode 缓存是值对象完整设计
- **my-xhs**：**该用没用**——网关路由自动发现（my-xhs 网关动态路由参考——从服务端点元数据生成路由）

### 模块: `webmvc` 35 + `webflux` 24 + `jdbc` 7 + `guice` 3（批 6：69 文件——仓库收官）

#### KP-224 `EnableWebMvcExtension`/`EnableWebFluxExtension` 双扩展注解（EnableWebMvcExtension.java:78-182 + EnableWebFluxExtension.java:71-121）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（@Enable 扩展） | **置信度**：High
- **前置**：@EnableWebMvc、@Import、@AliasFor、BeanSource 枚举（BEAN_FACTORY/SPRING_FACTORIES/JAVA_SERVICE_PROVIDER）
- **需求**：**MVC/WebFlux 的模块化增强启用**——开关式启用端点注册/拦截/事件（MVC 与 WebFlux 对称设计）
- **参考实现**：**对称双注解**（MVC :83 `@Import(WebMvcExtensionBeanDefinitionRegistrar.class)` / WebFlux :76 `@Import(WebFluxExtensionBeanDefinitionRegistrar.class)`——**同构 Registrar**）；**共享元注解**（:81/:74 `@EnableWebExtension`——**公共能力元注解实证**（EnableWebExtension.java:58-63——@Import(WebExtensionBeanDefinitionRegistrar) :62——**两级 Import 链**：EnableWebMvcExtension → EnableWebExtension → Registrar）；**@OverrideAnnotationAttributes 实证**（:82/:75——**自研注解**（OverrideAnnotationAttributes.java:79——`@Target(ANNOTATION_TYPE)` + **strategy 属性**（:89——默认 `ConfigurationPropertyOverrideAnnotationAttributesStrategy`——**可插拔覆盖策略**（注解属性覆盖的 SPI 化）——处理链实证：BeanCapableImportCandidate/ConfigurationPropertyOverrideAnnotationAttributesStrategy/ImportOptional 三处使用））；**@AliasFor 透传**（:97-182——registerWebEndpointMappings/interceptHandlerMethods/publishEvents/requestContextStrategy/sources 别名到 @EnableWebExtension——多属性透传）；**三源枚举**（:182 sources——BEAN_FACTORY/SPRING_FACTORIES/JAVA_SERVICE_PROVIDER——Bean 发现的三种来源）
- **对比取舍**：**知识增量**：①**MVC/WebFlux 对称扩展**（同一能力两栈各一套 Registrar——WebRequestRule 的框架无关（KP-221）在此对称落地）；②**@OverrideAnnotationAttributes 是策略化注解覆盖**（非"合并"——**strategy SPI 决定如何覆盖**（可插拔），比固定合并更灵活——Spring 用 @AliasFor 固定合并，microsphere 是策略注入）；③**两级 Import 链**（Enable → Enable → Registrar——注解分层）；④sources 三源枚举（Bean 发现来源显式化）
- **my-xhs**：**该用没用**——@Enable 模块化开关模式参考（my-xhs 可做自定义 EnableXxx）

#### KP-225 拦截器与过滤链增强（webmvc：LazyCompositeHandlerInterceptor/AnnotatedMethodHandlerInterceptor/ContentCachingFilter + 存储型 Advice）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：HandlerInterceptor、RequestBodyAdvice/ResponseBodyAdvice、ContentCachingFilter
- **需求**：**MVC 请求处理增强**——懒组合拦截器/注解方法拦截/请求体缓存
- **参考实现**：**LazyCompositeHandlerInterceptor**（懒组合——多拦截器合并惰性初始化）；**AnnotatedMethodHandlerInterceptor**（注解驱动方法拦截）；**ContentCachingFilter**（内容缓存——日志/审计）；**StoringRequestBodyArgumentAdvice/StoringResponseBodyReturnValueAdvice**（存储型 Advice——**请求体存取**——RequestBodyAdvice/ResponseBodyAdvice 实现）
- **my-xhs**：**该用没用**——请求日志/审计（ContentCachingFilter 模式）；Spring 官方 Filter 覆盖基础

#### KP-226 `EnableP6DataSource` P6Spy 集成（EnableP6DataSource.java:38-47 + P6DataSourceBeanPostProcessor:41-50 + 家族）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式]（数据源代理） | **置信度**：High
- **前置**：P6Spy（SQL 日志代理）、@Import 多类、BeanPostProcessor
- **需求**：**SQL 日志代理**——启用 P6Spy 包装数据源（SQL 执行日志/慢 SQL）
- **参考实现**：**@Import 多类装配**（:41-45——P6DataSourceBeanDefinitionRegistrar + SpringProtocolURLStreamHandler + SpringP6SpyURLConnectionFactory——**URL 协议复用**（KP-219 spring:// 协议挂 P6Spy 连接））；**P6DataSourceBeanPostProcessor**（:41——`extends GenericBeanPostProcessorAdapter<DataSource>`（**泛型 BPP 适配器实证**：GenericBeanPostProcessorAdapter.java:42——`implements BeanPostProcessor` + **泛型类型解析**（:47 "resolving the generic bean type"——构造时解析泛型实参限定 bean 类型——**类型化后处理器**（只处理匹配类型的 Bean，免强转）））+ `doPostProcessAfterInitialization` 包装 :50 + **排除名单**（:45 `microsphere.jdbc.p6spy.excluded-datasource-beans`——配置化排除））；CompoundJdbcEventListenerFactory
- **对比取舍**：**知识增量**：①**泛型 BPP 适配器**（GenericBeanPostProcessorAdapter\<T>——类型化后处理器：构造解析泛型实参（:47）→ 只对匹配类型 Bean 生效——**类型过滤内建**）；②**@Import 多类装配 + URL 协议集成**（跨模块复用）；③P6Spy vs Druid/MyBatis SQL 日志（同功能不同代理层）
- **my-xhs**：**该用没用**——SQL 日志/慢 SQL（my-xhs 可用 P6Spy 或 Druid 自带）

#### KP-223 web 事件与注解配套（web/event 3 + web/annotation 2 + web/util 13）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：Medium（部分深读）
- **前置**：Web 请求事件、@Enable 元注解
- **需求**：web 事件（HandlerMethodArgumentsResolvedEvent/WebEndpointMappingsReadyEvent——**方法参数解析完成/端点映射就绪事件**）+ 发布器（WebEventPublisher）+ @EnableWebExtension（:58-63——**KP-224 两级 Import 链的上层元注解**）+ util（HttpUtils/MediaTypeUtils/RequestContextStrategy/WebScope/WebType 等）
- **参考实现**：**事件**（HandlerMethodArgumentsResolvedEvent——参数解析事件 / WebEndpointMappingsReadyEvent——端点映射就绪——**配合 KP-222 端点元数据**）；**@EnableWebExtension**（EnableWebExtension.java:58-63——@Import(WebExtensionBeanDefinitionRegistrar)——**KP-224 引用链实证**）；**RequestContextStrategy**（请求上下文策略——MVC/WebFlux 抽象）
- **my-xhs**：**该用没用**——web 请求生命周期事件（埋点）；Spring 官方 WebRequest 覆盖基础

#### KP-227 webflux 核心（webflux 24 文件——批 2 提）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式]（reactive 扩展） | **置信度**：High
- **前置**：WebFlux/WebFilter/HandlerMethod/请求上下文
- **需求**：**WebFlux 增强**——过滤器链（CompositeWebFilter/DelegatingWebFilter）、端点映射工厂（HandlerMapping/HandlerMetadata/RequestMappingMetadata 三工厂）、请求处理（InterceptingHandlerMethodProcessor/StoringRequestBody 拦截器）、事件（ServerRequestHandledEvent）
- **参考实现**：**过滤器组合**（CompositeWebFilter + DelegatingWebFilter——**WebFilter 组合/委托**）；**端点映射三工厂**（HandlerMappingWebEndpointMappingFactory/HandlerMetadataWebEndpointMappingFactory/RequestMappingMetadataWebEndpointMappingFactory——**KP-222 端点元数据的 WebFlux 落地**）；**请求上下文**（RequestContextWebFilter——**上下文传播 Filter**）；**ServerWebRequest**（自研封装）；WebServerScope/WebServerUtils（web 服务器作用域枚举 :35）
- **对比取舍**：**知识增量**：WebFlux 的端点元数据化（三工厂——reactive 侧对称 KP-222）；**WebFilter 组合模式**（reactive 过滤器链）
- **my-xhs**：**该用没用**——WebFlux 端点发现参考（若 my-xhs 用 reactive）

#### KP-228 Guice 注入桥接（guice 3 文件）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P3 | **过时**：[时间无关模式]（容器桥接） | **置信度**：High
- **前置**：Guice 容器、@Inject、Spring 注入处理
- **需求**：**Guice @Inject 注入 Spring Bean**——Spring 容器中处理 Guice 风格注入
- **参考实现**：**GuiceInjectAnnotationBeanPostProcessor**（:31——**extends AnnotatedInjectionBeanPostProcessor**（KP-205 家族复用！——**注解注入后处理器的泛化**）+ `ANNOTATION_TYPE = Inject.class`（:33——**注解类型参数化**）+ **optional 属性支持**（:42-47——@Inject(optional=true) 语义——**determineRequiredStatus 覆写**（Spring 默认 required，Guice optional 语义桥接）））；EnableGuice + GuiceConfiguration（@Enable 装配）
- **对比取舍**：**知识增量**：**跨容器注入桥**（Guice @Inject → Spring 注入）+ **注解注入后处理器泛化复用**（KP-205 的 AnnotatedInjectionBeanPostProcessor 被 Guice 复用——生态设计验证）；optional 语义差异（Guice 可空注入 vs Spring required）
- **my-xhs**：**不该用**——my-xhs 用 Spring 不用 Guice；但**注解注入后处理器的泛化模式**（注解类型参数化）值得学

---

## 三、深度 review 核对注记（2026-08-13 轮）

> **穷尽性核对（basename 级）**：323/323 生产文件——**168 个文件未提及 = 归组缺口（知识已覆盖但文件名未列全）**——铁律 #1"覆盖表声称 N/N 但正文缺"再犯（session005 交接"穷尽性"声称有水分）。
> **未提及族分布**（知识归属）：web/metadata 13（→KP-2xx 端点元数据族）/ context/event 12（→KP-2xx 事件拦截链）/ beans/factory/support 11（→BeanFactory 监听族）/ web/util 9 + web/rule 8（→Web 层扩展族）/ test 族 20+（→测试基座归组）/ core/env 6 + config/context/annotation 6（→Environment 双钩 + @Import 族）/ 其余散族——**全部可在既有 KP 找到知识落点**（无真遗漏）。
> **行动**：文件名级穷尽性未达成——后续提取归组必须列全文件名（铁律 #1 严格执行——本注记即为教训记录）。
