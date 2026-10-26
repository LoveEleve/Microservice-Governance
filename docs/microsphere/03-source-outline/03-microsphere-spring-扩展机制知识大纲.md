# Microsphere Spring 扩展机制知识大纲（microsphere-spring 触发面）

> 来源：`mapping/03-microsphere-spring.md` 33 个 KP 的知识本体提炼
> 性质：**源码侧 outline（L1.5）**——mapping 是文件映射（过程），本大纲是知识本体（结果）
> 组织：按知识维度，非按文件；每个知识点标注来源 KP + 六元元数据 + 现代替代
> 用途：①自学/面试知识图谱（Spring 扩展机制）②与课程 L1 合并成 L3 的源码侧素材 ③与 Spring 官方源码对照（本地有）
> 覆盖核对：33/33 KP 全部归属（文末核对表）

---

## 一、Spring 生态适配——"进 Spring 用 Spring 的" [工程问题]

> **核心命题**：microsphere 在 Spring 生态的关键纪律——**接口/SPI/排序全用 Spring 的**（SpringFactoriesLoader/Ordered/@Import/@AliasFor），不用自家 JDK 版。这是生态适配的最佳实践。

### 1.1 生态 SPI 选择（JDK SPI vs SpringFactoriesLoader）[🔴 P1] [时间无关模式]
- **来源**：KP-202
- **机制**：注入点解析器用 `SpringFactoriesLoaderUtils.loadFactories`（封装 Spring 官方 `org.springframework.core.io.support.SpringFactoriesLoader`）——**META-INF/spring.factories** 而非 JDK SPI
- **对比**：microsphere-java（纯 JDK 生态）用 ServiceLoader；本仓库（Spring 生态）用 spring.factories——**同一作者两生态的正确选择**
- **my-xhs**：该用没用——SpringFactoriesLoader 机制必懂（my-xhs 已用 Spring）

### 1.2 排序接口：Ordered（非 Prioritized）[🟡 P2] [时间无关模式]
- **来源**：KP-206（ApplicationEventInterceptor extends Ordered）
- **机制**：Spring 生态用 `org.springframework.core.Ordered`——与 microsphere-java 的 Prioritized 对照——**生态接口适配**
- **my-xhs**：该用没用——Spring Ordered 语义必懂

### 1.3 注解透传：@AliasFor 组合 [🔴 P1] [时间无关模式]
- **来源**：KP-217（@EnableTTLCaching）/ KP-224（@EnableWebMvcExtension）
- **机制**：`@AliasFor(annotation = Xxx.class)` 属性别名透传（proxyTargetClass/mode/order 等）——**注解增强外壳**（官方注解的"增强版"）
- **对比**：Spring 官方 @AliasFor 固定合并 vs microsphere @OverrideAnnotationAttributes **策略化覆盖**（strategy SPI 可插拔——:89）
- **my-xhs**：该用没用——自定义 EnableXxx 注解参考

---

## 二、Spring 内部机制的扩展点教学（本仓库核心价值）[工程问题]

> **核心命题**：microsphere-spring = Spring 内部机制（BeanFactory/事件/Environment/注解）的**扩展点教学宝库**——每个 KP 都是一个"Spring 少用扩展点"的示范。

### 2.1 依赖注入解析 SPI（BeanFactory 级）[🔴 P1] [时间无关模式]
- **来源**：KP-201~205
- **机制**：**注入点四形态统一**（Field/Method/Constructor/Parameter :83-112）+ 组合注册中心（forEach 全量收集）+ **并行依赖解析**（CompletionService 提交-收集 :205 + awaitTermination :230）+ 依赖树遍历
- **对比**：Spring 内部依赖图懒构建 vs microsphere **主动全量预构建**（启动拓扑/优雅停机用）
- **my-xhs**：该用没用——启动依赖分析参考

### 2.2 事件拦截链（多播器级 AOP）[🔴 P1] [时间无关模式]
- **来源**：KP-206/207/208/209/210
- **机制**：**两级拦截**（事件级 + 监听器级）+ **回调式责任链**（拦截器手动 `chain.intercept()` 放行——不调则链短路吞事件——**AOP 环绕语义的事件版**——Servlet Filter 同款）+ **继承官方多播器**（extends SimpleApplicationEventMulticaster + final 防重写）
- **my-xhs**：该用没用——Spring 事件统一埋点/审计

### 2.3 BeanFactory 三时点生命周期监听 [🔴 P2] [时间无关模式]
- **来源**：KP-208/209
- **机制**：onBeanDefinitionRegistryReady（注册）→ onBeanFactoryReady（就绪）→ onBeanFactoryConfigurationFrozen（**冻结——Spring 少用扩展点**）+ **并行预实例化应用**（parallel preInstantiateSingletons——启动加速，配置化线程数）
- **my-xhs**：该用没用——启动加速候选（风险：Bean 顺序依赖）

### 2.4 Environment 四组双钩监听 [🔴 P2] [时间无关模式]
- **来源**：KP-214
- **机制**：before/after 成对钩子 × 4 数据面（propertySources/systemProperties/systemEnvironment/merge :47-108）——**Environment 级 AOP 化**
- **my-xhs**：该用没用——Environment 审计/埋点

### 2.5 泛型 BPP 适配器（类型化后处理器）[🟡 P2] [时间无关模式]
- **来源**：KP-226
- **机制**：`GenericBeanPostProcessorAdapter<T>`——构造解析泛型实参（:47）→ 只对匹配类型 Bean 生效（类型过滤内建，免强转）
- **my-xhs**：该用没用——自定义 BPP 参考（通用模式）

### 2.6 注解驱动 @Import 模板 + 可选导入 [🔴 P1] [时间无关模式]
- **来源**：KP-211/211b/211c
- **机制**：**ImportSelector 模板化**（AnnotatedBeanCapableImportCandidate → Selector——泛型注解类型 ResolvableType 解析 + final selectImports 模板）+ **ResolvablePlaceholderAnnotationAttributes**（注解属性支持 ${placeholder}——Spring 官方缺失）+ **isEnabled 环境开关**（两级回退默认 true）+ **@ImportOptional**（类存在性探测，不存在跳过——Spring @Import 硬失败的容错版）+ @OverrideAnnotationAttributes 策略化覆盖
- **my-xhs**：该用没用——自定义 @EnableXxx 模板基座参考

### 2.7 跨生态转换桥 [🔴 P1] [时间无关模式]
- **来源**：KP-216
- **机制**：`SpringConverterAdapter implements ConditionalGenericConverter`——SPI 加载全部 microsphere Converter → ConvertiblePair 双键映射 → **无缝接入 Spring ConversionService**（生态整合范式）
- **my-xhs**：该用没用——自定义 Converter 接入 Spring 参考

---

## 三、配置管理扩展 [分布式问题]

### 3.1 @PropertySource 元注解增强（10 属性）[🔴 P1] [时间无关模式]
- **来源**：KP-212
- **机制**：**排序三件**（first/before/after——官方无法控制顺序）+ autoRefreshed（自动刷新）+ encoding 占位符默认值（${file.encoding:UTF-8}）+ resourceComparator——**元注解扩展官方注解**（@Target(ANNOTATION_TYPE) 组合模式）
- **my-xhs**：该用没用——多配置源优先级控制

### 3.2 配置变更事件化 [🔴 P1] [时间无关模式]
- **来源**：KP-213
- **机制**：PropertySourceChangedEvent（单源）/PropertySourcesChangedEvent（整体）——**两级粒度**——Spring 官方把刷新放 Cloud 层，microsphere **沉到底层**（分层设计对照）
- **my-xhs**：该用没用——配置热更新（对接 Nacos 变更通知）

---

## 四、Web 层扩展 [工程问题]

### 4.1 框架无关请求匹配（解决 MVC/WebFlux 重复）[🔴 P1] [时间无关模式]
- **来源**：KP-221
- **机制**：`WebRequestRule` + **泛型模板基座**（AbstractWebRequestRule\<T>——六大规则：Methods\<String>/Params\<Expression>/Headers/Consumes/Produces/Pattens）+ Composite AND 组合——**Spring 官方在 servlet 和 reactive 包重复定义两套 RequestCondition，microsphere 一套通用**
- **拼写教训**：WebRequest**Pattens**Rule（Pattens 拼错——StacKTrace 后第二例）
- **my-xhs**：该用没用——网关/过滤器框架无关匹配

### 4.2 端点映射元数据（十字段模型）[🔴 P2] [时间无关模式]
- **来源**：KP-222
- **机制**：patterns/methods/params/headers/consumes/produces/negated 八条件 + Builder + hashCode 缓存——**MVC 内部端点信息外部化**（网关路由发现/监控基础）
- **my-xhs**：该用没用——网关动态路由参考

### 4.3 @Enable 对称双注解 + 两级 Import 链 [🔴 P1] [时间无关模式]
- **来源**：KP-224
- **机制**：EnableWebMvcExtension/EnableWebFluxExtension 对称 + @EnableWebExtension 元注解 + **两级 Import 链**（Enable → Enable → Registrar）+ @OverrideAnnotationAttributes **策略化覆盖**（strategy SPI）
- **my-xhs**：该用没用——模块化开关模式

---

## 五、缓存与 SQL 代理 [工程问题]

### 5.1 TTL 缓存体系（cacheResolver 注入点）[🔴 P2] [时间无关模式]
- **来源**：KP-217/218
- **机制**：ThreadLocal TTL 上下文（finally 清理防泄漏）+ @Cacheable(cacheResolver = BEAN_NAME) **官方扩展点利用** + @Bean(name) 装配 + **TTLRedisCacheWriterWrapper 死代码实证**（99 注释/0 代码——"看起来在做≠真的实现"）
- **my-xhs**：该用没用——声明式 TTL（Redis 过期控制）

### 5.2 P6Spy SQL 代理集成 [🟡 P2] [时间无关模式]
- **来源**：KP-226
- **机制**：@Import 多类 + URL 协议复用（spring:// 挂 P6Spy）+ 泛型 BPP + 排除名单配置
- **my-xhs**：该用没用——SQL 日志/慢 SQL

---

## 覆盖核对（33/33）

| 维度 | 覆盖 KP | 数 |
|------|---------|:--:|
| 一、生态适配 | 202,206,217,224 | 4 |
| 二、Spring 扩展点 | 201,203,204,205,207,208,209,210,211,211b,211c,211d,214,216,216b,226,228 | 17 |
| 三、配置管理 | 212,213,215,216c | 4 |
| 四、Web 层 | 221,222,223,224,225,227 | 6 |
| 五、缓存/SQL | 217,218,226 | 3 |

**去重后唯一 KP**：201-228（含 211b-d/216b-c 拆分）全部 = **33/33 ✓**
**无孤儿 KP** ✓（KP-216 双归属 二/三；KP-224 双归属 一/四；KP-226 双归属 二/五）
