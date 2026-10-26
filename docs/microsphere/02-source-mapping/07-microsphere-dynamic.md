# microsphere-dynamic 知识点提取

> 源码：`/data/workspace/java-training-camp/cloud-native-code/stage-4/microsphere-dynamic`（依赖链第 7 站，multiactive 之后；单模块 microsphere-dynamic-jdbc-spring-boot，81 生产文件 + 46 测试）
> 提取时间：2026-08-13（批 1：context 核心 17 文件；批 2：config + constants；批 3：context 注册器族 + env；批 4：datasource + autoconfigure；批 5：mybatis + mybatisplus；批 6：shardingsphere + transaction + util）
> 状态：批 1 已提取；MCP 索引已建（1754 节点/4622 边）
> 关联：multiactive（06 仓库 ZoneContextChangedEvent 消费方——本仓库 PropagatingDynamicJdbcConfigChangedEventListener 实证）；stage-4 课程（多活架构存储侧）
> 历史交叉验证：`microsphere-analysis/18-microsphere-dynamic-analysis/18-REQ-requirements-spec.md`（REQ-001~005 + 缺陷表）+ 14 篇分析

## 一、仓库定位

**"数据库基础设施即配置"的 Spring Boot Starter**——一段 JSON 配置描述一个数据库单元（DataSource + MyBatis + TransactionManager + 可选 ShardingSphere），Starter 自动为每单元拉起**独立 Spring 子上下文**（非 ThreadLocal 路由——完整 Bean 生命周期隔离）。核心维度：[分布式问题]（多活存储隔离）+ [工程问题]（子上下文/配置驱动）。

**与 baomidou dynamic-datasource 根本区别**（REQ 表实证）：@DS 注解+ThreadLocal 路由 vs **Spring 子上下文**（AnnotationConfigApplicationContext per unit）；配置级隔离（每单元独立 SqlSessionFactory+TransactionManager）；多 ORM 共存；ShardingSphere 原生支持；**Zone 感知**（ha-datasource 按 zone 分组，与 06 仓库联动）；配置热更新（事件驱动重建）。

**依赖链**（pom 实证）：microsphere-spring-boot-core + **microsphere-multiactive-spring**（Zone 事件来源）+ spring-boot/actuator + mybatis-spring-boot-starter + mybatis-plus-boot-starter + shardingsphere-jdbc-core-spring-boot-starter + HikariCP + mysql-connector + mariaDB4j（测试）

## 前置条件清单

读者需先掌握：1. 前六仓库全部知识点（尤其 06 仓库 ZoneContextChangedEvent——本仓库消费方）2. Spring 子上下文（AnnotationConfigApplicationContext/Environment merge/BeanFactoryPostProcessor 时序）3. Boot 自动配置机制（AutoConfigurationImportSelector/条件）4. MyBatis/MyBatis-Plus/ShardingSphere 生态 5. baomidou dynamic-datasource（对照对象）
未达前置者，先补：前六仓库 outline + spring-framework BeanFactory 生命周期

## 掌握度

目标读者：中级偏上（读源码多、Spring 熟——HANDOVER 画像）
讲解策略：与 baomidou dynamic-datasource 对照 + Spring 子上下文机制深潜（18-04 分析重点）

---

## 二、逐文件映射 + 原子记录

### 包: `io.microsphere.dynamic.jdbc.spring.boot.context`（批 1：context 17 文件核心）

#### KP-601 `DynamicJdbcChildContext` 子上下文基座（DynamicJdbcChildContext.java:30-151）

- **维度**：[工程问题]（Spring 子上下文）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（子上下文模式） | **置信度**：High
- **前置**：AnnotationConfigApplicationContext、ConfigurableEnvironment.merge、ConfigurationPropertySources.attach（Boot Binder）、BeanFactoryPostProcessor 时序（postProcessBeanFactory）
- **需求**：**每数据库单元一个独立子上下文**——隔离 Bean 定义/AutoConfiguration/属性，完整生命周期（REQ-003）
- **参考实现**：**extends AnnotationConfigApplicationContext**（:30——子类化官方上下文）；**构造注入**（:42-49——DynamicJdbcConfig + 属性名 + 父上下文 + IdGenerator（:47——`generator.generate(...)` 生成上下文 id））；**环境合并**（mergeParentEnvironment :60-68——`environment.merge(parentEnvironment)`（:64）+ **移除合成 PropertySource**（:66/:70-82——删掉动态合成的源，防递归污染）+ **detachConfigurationPropertySources**（:84-90——ATTACHED_PROPERTY_SOURCE_NAME 源清理（attach 源指向自身时移除——Boot Binder attach 的脏源））；**postProcessBeanFactory 覆写**（:92-107——**关键时序钩子**：setClassLoader（:96 父加载器）+ prepareEnvironment（:98——**ConfigurationPropertySources.attach**（:117——Boot Binder 需要）+ 注册子上下文刷新监听器（:100）+ registerConfigurationClasses（:102——默认注册 DynamicJdbcChildContextConfiguration（:139-141 setupConfigurationClasses——子类可覆写扩展））+ customizeBeanFactory（:104 扩展点）+ **processDynamicJdbcChildContext**（:106——DynamicJdbcContextProcessor 管道入口））
- **对比取舍**：**知识增量**：①**子上下文继承 + 钩子重排**（覆写 postProcessBeanFactory 注入全部定制逻辑——**官方上下文的扩展点利用**）；②**Environment merge + 合成源清理**（:66——父子属性合并的正确姿势——**合成源防递归**（动态生成的源不能再 merge 回去））；③**id 生成器注入**（:47——上下文标识策略 SPI）
- **my-xhs**：**该用没用（实证）**——my-xhs 无子上下文（AnnotationConfigApplicationContext MCP 搜索 0 结果）——单上下文 Bean（无多单元隔离）

#### KP-602 `DynamicJdbcContextProcessor` 四 SPI 管道（DynamicJdbcContextProcessor.java:40-247）

- **维度**：[工程问题]（管道架构）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（管道+SPI） | **置信度**：High
- **前置**：SpringFactoriesLoader（微球版 loadFactories）、BeanDefinitionRegistry、MapPropertySource、AutoConfigurationImportFilter 排除
- **需求**：**4 个 SPI × 6 模块的管道**——默认值填充（ConfigPostProcessor）→ 校验（ConfigValidator）→ 属性合成（ConfigConfigurationPropertiesSynthesizer）→ Bean 注册（ConfigBeanDefinitionRegistrar）（REQ-002）
- **参考实现**：**五步管道**（process :50-71——①registerAnnotationConfigProcessors（:73-76——AnnotationConfigUtils 注册注解处理器）②postProcessDynamicJdbcConfig（:78-82——ConfigPostProcessor 逐模块填充默认值）③validateDynamicJdbcConfig（:84-95——ConfigValidator 收集 ValidationErrors，无效抛 ConfigValidationException）④processDynamic（:97-102——**dynamic 模式**：注册 DynamicDataSource Bean（:104-110）+ **从 config 移除 datasource/ha-datasource/shardingsphere 配置**（:112-119——`setDataSource(emptyList())`/`setHighAvailabilityDataSource(emptyMap())`/`setShardingSphere(null)`——**ha-datasource 实证 = DynamicJdbcConfig 字段而非独立包**（历史 REQ-004 修正））⑤processDynamicJdbcConfigurationProperties（:132-139——Synthesizer 合成 → **MapPropertySource 插入**（:150-166——findConfiguredPropertySourceName 找当前源 → addAfter（:161）或 addFirst（:163）——**优先级紧贴配置源之后**）+ 子上下文排除 AutoConfiguration（:168-177——addExclusionAutoConfigurationPropertySource——`spring.autoconfigure.exclude` 合成源）⑥registerDynamicJdbcConfigBeanDefinitions（:179-186——Registrar 逐模块注册 Bean））；**SPI 懒加载**（:218-246——4 个 SPI 全部 `loadFactories(context, Xxx.class)`（SpringFactories）+ 字段缓存——**零配置扩展点**（新模块 = 实现接口 + spring.factories 注册——REQ-002"新增模块只需实现接口"）
- **对比取舍**：**知识增量**：①**四阶段管道 + SPI 扩展**（vs 硬编码模块判断——**管道化架构**（每阶段一组 SPI 实现，模块按需参与））；②**PropertySource 插入位置策略**（addAfter 配置源——**合成属性覆盖父配置**（子上下文优先））；③**dynamic 模式的配置篡改**（:112-119——注册 DynamicDataSource 后**原地清空 config 的 datasource 字段**（可变对象副作用——设计取舍：防止后续模块重复处理——**对调用方可见的副作用**（传入了克隆 :124——cloneDynamicJdbcConfig——注册用克隆，原 config 被清空——**调用链顺序敏感**））；④**AutoConfiguration 排除合成**（:168-177——子上下文禁用父模块自动配置（防止父上下文重复建 DataSource））
- **测试佐证**：DynamicJdbcContextApplicationListenerTest（单配置：3 configs 断言——shardingJdbcConfigs 1 + primaryConfig 名 + dataSourceMap 1 + **ShardingSphereDataSource 解包**（:70——unwrap 断言）+ mappers 4）；MultipleContextTest（多配置：3 configs + mappers 12 + transactionManagers 1（myTransaction）——**多子上下文并存实证**）
- **my-xhs**：**该用没用（实证）**——my-xhs 无 4 SPI 管道（模块化 SPI 族 MCP 搜索 0 结果——单数据源无多模块需求）

#### KP-603 `DynamicJdbcContextApplicationListener` 启动装配入口（DynamicJdbcContextApplicationListener.java:35-197）

- **维度**：[工程问题]（启动时序）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（ApplicationPreparedEvent 装配） | **置信度**：High
- **前置**：ApplicationPreparedEvent（Boot 启动早期——context 创建后、refresh 前）、OnceMainApplicationPreparedEventListener（微球基类——只处理主上下文）、线程池、启动失败聚合
- **需求**：**启动时装配所有数据库单元**——读取全部 DynamicJdbcConfig → 多配置并行建子上下文 / 单配置直接用当前上下文
- **参考实现**：**监听 ApplicationPreparedEvent**（:35——extends OnceMainApplicationPreparedEventListener（微球——只对主上下文生效一次）+ DEFAULT_ORDER=200（:40——启动早期）；**开关**（isDisable :58-60——`microsphere.dynamic.jdbc.enabled` 属性）；**多/单分支**（processDynamicJdbcContext :62-92——configs.size()==0 忽略（:69-72）；**多配置 → processDynamicJdbcChildContexts**（:83——**每配置一个子上下文**）；**单配置 → processDynamicJdbcContext**（:88——**直接用当前上下文**（不建子上下文——单单元场景省一层））；**并行初始化**（:116-160——`newFixedThreadPool(parallelism)`（:120——**每配置一线程并行建子上下文**）+ awaitTermination 循环（:140-150——轮询完成）+ **InitializeErrors 收集**（:121/:128-132——失败不中断其他单元——**并行容错聚合**）+ 有错抛 DynamicJdbcInitializeException（:152-154——启动失败）；**父上下文 AutoConfiguration 排除**（:178-189——appendExclusionAutoConfigurationProperty——多上下文时父上下文排除模块自动配置（:182-188——配置了排除类名用配置，否则 getMultipleContextExclusionAutoConfigurationClassNames 全模块——**防父上下文抢建数据库 Bean**））；**ShardingSphere 关闭钩子注册**（:99-104——有 sharding config 才注册 SyncExecutionShutdownHookApplicationListener（:101-103））；**Propagating 事件监听注册**（:94-97——registerPropagatingDynamicJdbcConfigChangedEventListener——变更传播监听（KP-605））
- **对比取舍**：**知识增量**：①**启动早期装配窗口**（ApplicationPreparedEvent——Boot 生命周期中"context 已建未 refresh"——**子上下文装配的正确时机**（refresh 前注册，随主上下文启动））；②**多配置并行 + 失败聚合**（:116-160——**启动并行的完整模式**（线程池 + 轮询 + 错误收集 + 聚合异常）——注意：**awaitTermination 循环是忙等轮询**（:140-150——completedTaskCount==parallelism break——**无 shutdownNow 直到完成**（:158 完成后才 shutdownNow）——若某任务死锁则启动挂死）；③**单配置免子上下文**（:85-89——性能优化分支——**子上下文不是必须**（单单元直接用主上下文——多配置才隔离））
- **测试佐证**：同上（单/多配置端到端 + MariaDB4j 真实数据库（AbstractMariaDB4jTest 基类——**真实 MySQL 环境集成测试**））
- **my-xhs**：**该用没用（实证）**——my-xhs 无多单元装配（ApplicationPreparedEvent 监听族 0 结果）

#### KP-604 `DynamicJdbcChildContextRefreshedListener` 子上下文 Bean 回注父上下文（DynamicJdbcChildContextRefreshedListener.java:33-155）

- **维度**：[工程问题]（跨上下文 Bean 注册）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（Bean 回注+命名策略） | **置信度**：High
- **前置**：ContextRefreshedEvent、BeanDefinitionRegistry.registerBeanDefinition、FactoryBean 注册、基础设施 Bean（findInfrastructureBeanNames）
- **需求**：**子上下文刷新后把业务 Bean 注册到父上下文**——父应用代码能 @Autowired 子上下文的 Mapper/DataSource/TransactionManager——**跨上下文暴露**（REQ-003 隔离与暴露的平衡）
- **参考实现**：**监听 ContextRefreshedEvent**（:33——子上下文刷新完成时触发）；**父关闭级联**（:70-74——`parentContext.addApplicationListener(ContextClosedEvent → childContext.close())`——**父亡子随**（级联关闭））；**Bean 回注**（registerParentBeansFromChildContext :76-91——registerParentBeans 标志（子上下文 registerParentBeans() :56-58 设置）——**基础设施 Bean 跳过**（:82-84——isInfrastructureBean 白名单（:93-95——findInfrastructureBeanNames 收集）——BeanFactory/Environment 等不暴露）+ **Bean 名生成**（:97-105——**ParentContextBeanNameGenerator SPI**（:100——按子 Bean 类型匹配生成器（:112-115——getChildBeanType().isAssignableFrom））+ 默认 `childContextId + "$" + beanName`（:107-110——**上下文 id 前缀防冲突**））；**暴露策略**（:117-124——**暴露类白名单**（isExposedBeanClass :126-135——multipleContextExposedBeanClasses 配置类集合）——白名单内 registerBean（可带 primary），否则 **registerFactoryBean**（:122——FactoryBean 包装）；**primary 判定**（:148-153——`dynamicJdbcConfig.isPrimary()` && 主 Bean 白名单（multipleContextPrimaryBeanClasses））
- **对比取舍**：**知识增量**：①**子→父 Bean 回注模式**（跨上下文暴露的正确姿势——**刷新后遍历 + 命名生成 + 基础设施排除**）；②**白名单双机制**（暴露类 + primary 类——**可配置的暴露策略**（哪些类直接注册、哪些类可当 primary））；③**FactoryBean 包装回注**（:122——非白名单 Bean 用 FactoryBean 包装——**延迟实例化**（父上下文不提前创建子 Bean））；④**Bean 名冲突治理**（:107-110——`contextId$beanName` 前缀——多子上下文同名 Bean 不冲突——**命名空间化**）；⑤**级联关闭**（:70-74——父关闭钩子注册——**子上下文生命周期管理**（防泄漏））
- **my-xhs**：**该用没用（实证）**——my-xhs 无跨上下文 Bean 回注（无子上下文）

#### KP-605 `PropagatingDynamicJdbcConfigChangedEventListener` 双事件传播（Zone + 配置变更）（PropagatingDynamicJdbcConfigChangedEventListener.java:31-108）

- **维度**：[分布式问题]（多活存储联动）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（事件传播链） | **置信度**：High
- **前置**：SmartApplicationListener（supportsEventType）、**ZoneContextChangedEvent（06 仓库 KP-510——本仓库消费方实证）**、PropertySourcesChangedEvent（03 仓库 KP-213）、DynamicJdbcConfig.hasHighAvailabilityDataSource
- **需求**：**配置变更 / Zone 切换 → DynamicJdbcConfigChangedEvent**——**Zone 切换时只重建含 HA DataSource 的单元**（REQ-004/005 事件链核心）
- **参考实现**：**双事件监听**（:49-50——supportsEventType：PropertySourcesChangedEvent 或 ZoneContextChangedEvent——**跨仓库事件关联实证**（06 仓库 KP-510 发布 → 本监听消费））；**配置变更传播**（onPropertySourcesChangedEvent :63-72——changedProperties 的 key **匹配 config 属性名**（:66——`dynamicJdbcConfigPropertyNames.contains(key)`）才 publish）；**Zone 变更传播**（onZoneContextChangedEvent :74-85——**isZoneChanged**（:87-97——检查事件携带的 propertyChangeEvents 中 "zone" 属性名——**只对 zone 属性变化响应**（其他属性如 enabled 变化不触发））+ **仅 HA config 触发**（:80——`dynamicJdbcConfig.hasHighAvailabilityDataSource()`——**非 HA 单元不随 zone 重建**（Zone 只影响 HA 数据源））；**publish DynamicJdbcConfigChangedEvent**（:99-107——含 config + propertyName——消费方 = datasource/DynamicDataSource（批 4 深读——重建触发点）
- **对比取舍**：**知识增量**：①**事件传播链**（PropertySourcesChangedEvent/ZoneContextChangedEvent → DynamicJdbcConfigChangedEvent → DynamicDataSource 重建——**跨仓库事件流的完整链路**（03→06→07 三仓库接力））；②**条件传播**（:66 属性名匹配 + :80 HA 判断——**传播过滤**（只传播相关变更——防全量重建））；③**zone 属性名硬编码**（:91——`"zone".equals(propertyChangeEvent.getPropertyName())`——**字符串字面量耦合**（未用常量——ZoneContext 属性名变化会静默失效——脆弱点）
- **my-xhs**：**已用（实证）**——my-xhs DynamicDataSource 直接 addPropertyChangeListener（06 仓库对照节——**my-xhs 用原生监听省去传播层**（无 PropertySourcesChangedEvent 桥接——配置热更新链路 my-xhs 待核对）——[批 4 深读对照]

#### KP-606 上下文配套（DynamicJdbcConfigChangedEvent:14-46 + DynamicJdbcChildContextIdGenerator + DynamicJdbcChildContextConfiguration:12-14 + ModuleProperties:14-60 + ParentContextBeanNameGenerator + error 2）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：ApplicationContextEvent、配置属性类、错误聚合
- **需求**：**配套载体**——事件对象/id 生成器/子上下文配置/模块属性基类/Bean 名生成 SPI/初始化错误
- **参考实现**：**事件载体**（DynamicJdbcConfigChangedEvent extends ApplicationContextEvent（:14——context + config + propertyName）；**id 生成器**（DynamicJdbcChildContextIdGenerator——DEFAULT 实例（:53——构造注入默认）；**子上下文配置**（DynamicJdbcChildContextConfiguration :12-13——**@EnableDynamicJdbcAutoConfiguration** 标记（子上下文启用动态 JDBC 自动配置——父上下文标记由 EnableDynamicJdbcAutoConfiguration 注解）；**模块属性基类**（ModuleProperties :14——dynamic + autoConfiguration + **bannedModules**（:20——**禁用模块集合**（模块级开关））；**Bean 名生成 SPI**（ParentContextBeanNameGenerator——getChildBeanType + generate——KP-604 消费）；**错误聚合**（InitializeErrors :11-37——**ConcurrentHashMap 收集**（:16——并行安全）+ putIfAbsent（:20——首错保留）+ toString 格式化（:32-36））；**初始化异常**（DynamicJdbcInitializeException extends RuntimeException :3）
- **my-xhs**：**不该用**——配套载体无独立知识（错误聚合模式可借鉴——ConcurrentHashMap + putIfAbsent）

### 包: `io.microsphere.dynamic.jdbc.spring.boot.config` + `constants`（批 2：config 9 + constants 1 文件）

#### KP-607 `DynamicJdbcConfig` 配置模型（Zone 感知）（DynamicJdbcConfig.java:40-207+）

- **维度**：[工程问题]（配置模型）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：Jackson @JsonProperty 映射、BeanFactoryAware、**ZoneContext（06 仓库）**、Map 配置结构
- **需求**：**一个数据库单元的完整配置模型**——datasource + ha-datasource（按 zone 分组）+ transaction + sharding-sphere + mybatis + mybatis-plus（REQ-001/004 的载体）
- **参考实现**：**模块字段**（:64-89——@JsonProperty(DynamicJdbcConstants.DATASOURCE_MODULE) 映射——dataSource（List\<Map> 通用场景 :65）/ **highAvailabilityDataSource**（:73-74——`Map<String, List<Map<String, Object>>>`——**zone 为 key 的数据源列表**——REQ-004 ha-datasource 实证（配置字段非独立包））/ transaction :80 / shardingSphere :83 / mybatis :86 / mybatisPlus :89）；**派生字段 @JsonIgnore**（:67-68/:76-77——dataSourcePropertiesList/highAvailabilityDataSourcePropertiesMap——字符串属性版本（PropertySource 合成用））；**动态/主开关**（:52 dynamic 默认 true（Dynamic 包装——动态数据源模式）/ :59 primary 默认 false（回注父上下文时是否 primary））；**EXCLUDE_FIELDS**（:42-43——序列化排除字段清单）；**Zone 感知**（:95——**持有 ZoneContext 字段**（config 级 zone 状态）+ implements BeanFactoryAware（:40——Aware 注入）；**状态辅助**（:182-207——hasDataSource/hasOnlySingleDataSource/hasHighAvailabilityDataSource/hasTransaction/hasShardingDataSource/hasMybatis/hasMybatisPlus——**配置完整性探测族**）
- **测试佐证**：DynamicJdbcConfigTest——JSON 绑定断言（:29）+ **has* 辅助全断言**（:48-56——getDataSourceSize 2/hasDataSource/hasTransaction/hasShardingDataSource/hasMybatis/hasMybatisPlus）+ 属性派生（:59-63——dataSourcePropertiesList 2 + dataSourcePropertiesMap containsKey("ds1")）
- **my-xhs**：**该用没用（实证）**——my-xhs 用 @ConfigurationProperties（无 JSON 字符串配置模型——DynamicJdbcConfig 类似物 0 结果）

#### KP-608 `ConfigurationCapable` 泛型反射取配置 + Aware 注入链（ConfigurationCapable.java:16-27 + DynamicJdbcConfigPostProcessor.java:17-37 + AbstractConfigPostProcessor.java:19-54 + Module.java:16-26）

- **维度**：[工程问题]（反射/模板）| **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：resolveActualTypeArgumentClass（02 仓库泛型模型）、ReflectionUtils.doWithMethods、Ordered 排序
- **需求**：**模块实现的配置类型绑定**——每个模块处理器（PostProcessor/Validator/Synthesizer/Registrar）声明自己的配置类型 C，基类自动从泛型解析
- **参考实现**：**泛型解析**（:18-20——`resolveActualTypeArgumentClass(getClass(), ConfigurationCapable.class, 0)`——**从实现类的泛型参数反射取配置类型**（02 仓库泛型模型的应用））；**反射 getter 取配置**（:22-27——ReflectionUtils.doWithMethods 按 `getName().startsWith("get") && returnType.equals(getConfigurationClass())` 匹配——**按返回类型定位 getter**（免手写 switch）；**Aware 注入基座**（AbstractConfigPostProcessor :19——implements ConfigPostProcessor + **ApplicationContextAware + EnvironmentAware + BeanClassLoaderAware**（:19——三 Aware 注入 + order 字段 :29）+ **AbstractConfigurationConfigPostProcessor**（:12-13——extends AbstractModuleConfigPostProcessor + ConfigurationCapable<C>——**泛型配置基座**（configurationClass 构造解析 :15-18）+ **AbstractModuleConfigPostProcessor** + **ModuleCapable**（模块名契约——getModule()——模块 SPI 的模块标识））；**name 缺省填充**（DynamicJdbcConfigPostProcessor :31-36——`FunctionUtils.setValueIfAbsent(name, () -> 属性名后缀)`——**未配 name 用属性名后缀**（microsphere.dynamic.jdbc.configs.orders → "orders"）+ **invokeAwareInterfaces**（:28——把 context 注入 config（BeanFactoryAware/ZoneContext 填充点——**配置对象 Aware 化**））；**模块标记注解**（Module :16-26——@Target TYPE/METHOD/FIELD——模块标注）
- **my-xhs**：**不该用**——反射技巧无独立场景（my-xhs 无模块化 SPI）

#### KP-609 校验 SPI 族（ConfigValidator:12-21 + ValidationErrors:19-43 + AbstractConfigurationConfigValidator:21-59 + ConfigValidationException）

- **维度**：[工程问题]（校验管道）| **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：ConfigValidator（校验契约）、模板方法、组件类型反射校验（Class.forName）、AbstractConfigValidator（:20-41——四 Aware 基座 + DynamicJdbcConfigValidator（默认校验器——name/属性基础校验））
- **需求**：**配置校验管道**——模块校验器收集错误 → 聚合 → 无效抛异常（REQ-002 校验阶段）
- **参考实现**：**校验契约**（ConfigValidator :21——validate(config, propertyName, errors)——**错误收集器传参**（非抛异常——多校验器聚合）；**错误载体**（ValidationErrors :19-43——List errorMessages（:23）+ addError 格式化 :31 + isValid :38 + toString 汇总 :43）；**模板校验器**（AbstractConfigurationConfigValidator :21-59——validate final（:25）→ validateConfiguration → **钩子族**（validateName :41 空实现/validateConfigurations :43/validateProperties :47 + **doValidate 抽象** :49——**模板方法**（子类只写 doValidate））；**组件类型校验**（validateComponentType :51-59——`Class.forName` 断言类存在 + 类型匹配（:59——`errors.addError("'{}' modules' '{}' property class '{}' is not the target type")`——**配置字符串类名合法性校验**（如 transaction manager class 必须是 PlatformTransactionManager 子类））；**异常**（ConfigValidationException extends RuntimeException :9）
- **my-xhs**：**该用没用（实证）**——my-xhs 无模块化校验（ConfigValidator 族 0 结果——校验管道模式可借鉴）

#### KP-610 `DynamicJdbcConstants` 常量 + 命名约定族（DynamicJdbcConstants.java:9-59）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟢 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：常量接口、命名约定
- **需求**：**模块名/前缀/命名约定集中**——模块名（datasource/ha-datasource/transaction/sharding-sphere/mybatis/mybatis-plus :11-21）+ 前缀体系（DYNAMIC_PREFIX="Dynamic#" :23/DYNAMIC_JDBC_PROPERTY_NAME_PREFIX="microsphere.dynamic.jdbc" :45 + configs :49）+ **Bean/上下文 id 命名模板**（:27-37——`DynamicJdbcConfigBean[...]`/`DynamicJdbcChildContext[...]`/`SynthesizedPropertySource[...]`——**统一命名空间的模板约定**）+ 配置键（enabled/configs/base-packages/property-name-aliases/modules/banned-modules :47-59）
- **my-xhs**：**不该用**——常量无独立知识

### 包: `context` 注册器族 + `env` 合成器族（批 3：13 文件）

#### KP-611 模块注册器族（ConfigBeanDefinitionRegistrar:14-24 + AbstractConfigBeanDefinitionRegistrar:26-60 + AbstractConfigurationConfigBeanDefinitionRegistrar:24-92 + AbstractModuleConfigBeanDefinitionRegistrar + AbstractScannedConfigurationConfigBeanDefinitionRegistrar + DynamicJdbcConfigBeanDefinitionRegistrar）

- **维度**：[工程问题]（BeanDefinition 注册）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（模板+注册） | **置信度**：High
- **前置**：BeanDefinitionRegistry、@Configuration 类注册（registerBeans/registerBean）、类名解析（resolveClassName）、ConfigurationCapable（KP-608）
- **需求**：**Bean 注册阶段的模块化**——每个模块（datasource/transaction/mybatis/...）注册自己的 Bean 定义——**按配置存在性支持判断**（无该模块配置就不注册）
- **参考实现**：**注册契约**（ConfigBeanDefinitionRegistrar :14——implements **EnvironmentCapable**（:14——注册器可拿 Environment）+ register(config, propertyName, registry) :23）；**四 Aware 基座**（AbstractConfigBeanDefinitionRegistrar :26-27——BeanClassLoaderAware/BeanFactoryAware/ApplicationContextAware/EnvironmentAware + **AnnotationConfigRegistry 探测**（:49-51——上下文若是注解注册器则持有——**注册 @Configuration 类的能力**））；**模块级支持判定**（AbstractConfigurationConfigBeanDefinitionRegistrar :34-45——supports()——`getConfiguration(dynamicJdbcConfig)` 为 null 返回 false——**无配置即跳过**（模块注册的门控）；**配置类注册**（:48-56——register final → registerConfigurationClasses（:58-77——配置类名列表 → resolveClassName 解析（:79-92——**字符串配置类名 → Class[]**——防类加载失败降级）+ registerBeans）→ 子类 register 钩子）；**扫描型变体**（AbstractScannedConfigurationConfigBeanDefinitionRegistrar——包扫描配置类注册）；**默认注册器**（DynamicJdbcConfigBeanDefinitionRegistrar——config 本身注册为 Bean）
- **对比取舍**：**知识增量**：①**模块注册器 + supports 门控**（模块独立性——无配置零开销）；②**字符串配置类名 → 注册**（:79-92——**配置驱动的类注册**（用户 JSON 写配置类名——反射解析——防错降级））；③**EnvironmentCapable 契约**（:14——注册器直接拿 Environment——**环境感知注册**）
- **my-xhs**：**该用没用（实证）**——my-xhs 无模块化注册（单数据源——ConfigBeanDefinitionRegistrar 族 0 结果）

#### KP-612 属性合成器族（ConfigConfigurationPropertiesSynthesizer:13-17 + AbstractConfigConfigurationPropertiesSynthesizer:39-75 + AbstractConfigurationConfigConfigurationPropertiesSynthesizer + AbstractModuleConfigConfigurationPropertiesSynthesizer + ConfigurationPropertiesFlatter:22-27）

- **维度**：[工程问题]（属性合成）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：@ConfigurationProperties 注解反射、Map 属性合成、前缀归一化
- **需求**：**JSON 配置 → Spring 属性键值**——把 DynamicJdbcConfig 的模块配置转成 `spring.datasource.*` 等标准属性（Synthesizer 是 4 SPI 的第 3 阶段——REQ-002）
- **参考实现**：**合成契约**（:15——synthesize(config, properties)——**填充目标 Map**）；**前缀反射**（:52-67——resolvePropertyNamePrefix——`AnnotationUtils.findAnnotation(ConfigurationProperties.class)`（:53——**@ConfigurationProperties 前缀反射提取**）+ prefix 空取 value（:60-62）+ normalizePrefix（:63——规范化成 `xxx.` 结尾）——**标准 Boot 绑定前缀复用**）；**排除合成**（:69-75——excludeAutoConfigurationProperty——**autoConfiguration 排除类名合成进属性**（子上下文禁用模块自动配置））；**嵌套变体**（AbstractConfigurationConfigConfigurationPropertiesSynthesizer——配置对象转属性；AbstractModuleConfigConfigurationPropertiesSynthesizer——模块级）；**扁平化器**（ConfigurationPropertiesFlatter :22-27——**单例**（:24）+ 嵌套属性扁平化（Map 嵌套 → 点分键——**JSON 嵌套结构 → 属性键转换**）
- **my-xhs**：**该用没用（实证）**——my-xhs @ConfigurationProperties 直接绑定（无 Synthesizer 族）

#### KP-613 环境配套（DynamicJdbcDefaultPropertiesPostProcessor:16-22 + SyncExecutionShutdownHookApplicationListener:19-54）

- **维度**：[工程问题]（启动/关闭时序）| **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：DefaultPropertiesPostProcessor（微球 SPI——默认属性资源）、ApplicationStartedEvent、ShardingSphere shutdown hook 线程
- **需求**：**默认属性注入 + ShardingSphere 关闭同步**——dynamic 的默认属性资源注册；sharding 场景关闭钩子线程同步化
- **参考实现**：**默认属性资源**（DynamicJdbcDefaultPropertiesPostProcessor :19-20——`defaultPropertiesResources.add(DEFAULT_PROPERTIES_LOCATION)`——**微球默认属性机制**（jar 内默认属性文件））；**关闭钩子同步执行**（SyncExecutionShutdownHookApplicationListener :19-53——ApplicationStartedEvent（:31）→ 按 ThreadFilter 找 ShardingSphere 钩子线程（:36/:50-52）→ ContextClosedEvent 时 **`Thread::run` 同步执行**（:46——**异步钩子变同步**（关闭时序保证：sharding 清理在子上下文关闭前完成）——注释"Sync execution using Thread#run method"）；**上下文匹配防御**（:33-34——非本上下文事件忽略（多上下文场景））
- **my-xhs**：**该用没用（实证）**——my-xhs 无关闭钩子同步（ShardingSphere 官方 Starter 自带关闭管理）

### 包: `datasource` + `autoconfigure`（批 4：12 文件）

#### KP-614 `DynamicDataSource` 事件驱动重建 + 延迟关闭（DynamicDataSource.java:40-328）

- **维度**：[分布式问题]（动态数据源）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（delegate 交换+延迟关闭） | **置信度**：High
- **前置**：DataSource 接口族、volatile 交换、ScheduledExecutorService 延迟关闭、DynamicJdbcConfigChangedEvent（KP-605）、InitializingBean/DisposableBean
- **需求**：**通用动态数据源**——配置变更（REQ-005）/Zone 切换（REQ-004）时**重建数据源**（换连接池——不是连接切换）——**delegate 模式 + 子上下文重建 + 延迟关闭**（旧池在途连接优雅完成）
- **参考实现**：**Delegate 包装**（:40——implements DataSource + InitializingBean + DisposableBean + BeanFactoryAware——全部 DataSource 方法委托（:102-145 委托族））；**懒初始化**（:82-94——afterPropertiesSet 只注册监听器（:88）+ delegate null 才 initialize（:90-92——注释"MultiChildContext dynamic datasource will be initialized twice"——**双初始化防御**（多子上下文场景 bean 会被两个上下文触发））；**getDelegate 懒建**（:147-153——**首访问才建子上下文**（延迟到首次连接——启动不建，用才建））；**重建核心**（initializeDataSource :174-201——**createDynamicDataSourceConfig**（:216-235——克隆 + **setDynamic(false) 防递归**（:226——避免 Processor 的 dynamic 分支再次包 DynamicDataSource）+ **移除其他模块配置**（:228-230——setTransaction(null)/setMybatis(null)/setMybatisPlus(null)——**只建 DataSource 子上下文**（瘦身隔离——MyBatis/事务在外的模块子上下文））+ name 重置（:232——Dynamic# 前缀）+ BeanFactory 注入）→ 新建子上下文（:178——专用 idGenerator :44-50——`DynamicJdbcConfigBean[...]` 风格）→ merge + refresh（:181-183）→ **getDataSource 唯一性校验**（:203-214——**多个 DataSource Bean 抛 IllegalStateException**（:206-208——子上下文必须单数据源））→ **synchronized(mutex) 原子交换**（:186-198——delegate + 子上下文成对交换——**volatile + mutex**（读无锁/写互斥））→ **旧子上下文延迟关闭**（:197/:238-251——closeScheduler.schedule(delay)（:245——**优雅迁移**（旧连接池延迟关闭——在途连接完成——`microsphere.dynamic.jdbc...close-delay` 可配 :78）））；**事件监听**（RefreshingDynamicDataSourceListener 内部类 :303-327——监听 **DynamicJdbcConfigChangedEvent**（:306——KP-605 传播的最终消费点——**事件链终点实证**）+ **属性名匹配**（:318——只响应自己的 config）+ **findParentContext 场景定位**（:280-298——单配置（parent 相等 :285）或多配置（parent 的 parent 相等 :292——**事件源 → 重建目标的上下文路径判定**）→ 重建（:321））；**销毁**（:96-100——关闭子上下文 + shutdown 调度器）
- **对比取舍**：**知识增量**：①**delegate 交换式动态数据源**（vs baomidou ThreadLocal 路由——**微球 = 整个 DataSource 对象替换**（连接池级切换——隔离最彻底/成本最高））；②**延迟关闭优雅迁移**（:245——旧池延迟 close——**在途连接平滑期**（生产级切换的正确姿势））；③**子上下文瘦身**（:228-230——重建子上下文只含 DataSource（排除 MyBatis——**重建粒度控制**——MyBatis 层由模块子上下文管））；④**事件链终点**（Propagating（KP-605）→ DynamicJdbcConfigChangedEvent → RefreshingDynamicDataSourceListener 重建——**双事件桥接 + 属性名过滤 + 上下文定位**的完整消费链）；⑤**mutex + volatile 交换**（:186——读写分离并发模型）；⑥**缺陷候选**：getDataSource 多 Bean 抛异常（:206——子上下文若有第二个 DataSource（如嵌入测试库）直接启动失败——**严格唯一性约束**）
- **测试佐证**：datasource 测试 2 个（[批 4 补扫]）+ DynamicJdbcContextApplicationListenerTest（unwrap 断言 :86——**DynamicDataSource 解包到 ShardingSphereDataSource**——delegate 链实证）
- **my-xhs**：**已用（实证 2026-08-13——架构差异重大发现）**——`my-xhs-common/.../zone/datasource/DynamicDataSource.java`（403 行）——**同名但机制不同**：**预建池切换架构**（构造 `(Map<String, DataSource> zoneDataSources, String defaultZone)`——**按 zone 预建连接池**（HA 数据源每 zone 一池）+ `switchDataSource(zone)` = **Map 查找换池** + **activeConnectionCount（AtomicInteger）+ SWITCH_WAIT_TIMEOUT_SECONDS=30 在途连接计数等待**（安全切换）+ addPropertyChangeListener 原生监听 ZoneContext）——**vs microsphere 重建架构**（子上下文新建 + delegate 交换 + 延迟关闭）——**两种动态数据源架构对照**：预建池切换（轻量/快/池级隔离——my-xhs 选型）vs 子上下文重建（重量/完整 Bean 生命周期隔离——microsphere 选型）——my-xhs 场景（单库 HA）预建池足够；my-xhs 无事件传播链/无子上下文——**简化取舍实证**

#### KP-615 datasource 模块配套（DataSourcePropertiesConfigPostProcessor:29-170 + JdbcURLAssembler:20-80 + DataSourceModuleProperties + DataSourceConstants + DataSourceConfigurationPropertiesSynthesizer + DataSourcePropertiesModuleValidator）

- **维度**：[工程问题]（默认值填充）| **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：DatabaseDriver（Boot——fromJdbcUrl 推导驱动）、JdbcURLAssembler（URL 组装）、setPropertyIfAbsent 模式
- **需求**：**datasource 模块的默认值填充**——driverClassName 推导/name/type/url 规范化（REQ-002 第 1 阶段实例）
- **参考实现**：**逐属性填充**（DataSourcePropertiesConfigPostProcessor :67-149——processDataSourceUrl（:91-101——**JdbcURLAssembler.assemble 规范化**（缺 scheme 补全——JDBC_URL_PREFIX "jdbc:" + PROTOCOL_SEPARATOR :24-28）+ **driverClassName 推导**（:103-122——`DatabaseDriver.fromJdbcUrl(jdbcURL).getDriverClassName()`（:120——**Boot 官方 DatabaseDriver 枚举推导驱动**——"从 jdbc:mysql://... 推导 com.mysql.cj.jdbc.Driver"（REQ-002 示例实证））+ **setPropertyIfAbsent 统一填充**（:160-170——缺省才补——**不覆盖用户显式配置**）+ **重名校验**（:36-39 validateDuplicated——重复数据源名报错））；**URL 组装器**（JdbcURLAssembler :20-80——defaultScheme（:32——Environment 注入 :37）+ normalize（:66——trim）+ assemble（:43-61——**补全 scheme 协议**（缺省 scheme 用默认——jdbc:mysql:// 补全））；**属性常量**（DataSourceConstants——NAME/TYPE/URL/DRIVER_CLASS_NAME/USER_NAME/PASSWORD）；**模块属性/合成器/校验器**（DataSourceModuleProperties/DataSourceConfigurationPropertiesSynthesizer/DataSourcePropertiesModuleValidator——SPI 三族 datasource 版）
- **my-xhs**：**已用（实证）**——my-xhs DynamicDataSource 同驱动配置机制（HikariCP）；DatabaseDriver 推导为 Boot 官方能力

#### KP-616 AutoConfiguration 拦截族（DynamicJdbcAutoConfigurationImportFilter:25-73 + DynamicJdbcAutoConfigurationImportListener:26-55 + DynamicJdbcAutoConfigurationImportSelector:27-67 + DynamicJdbcAutoConfigurationRepository:30-106 + EnableDynamicJdbcAutoConfiguration:22-23）

- **维度**：[工程问题]（自动配置控制）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（Import 三件套） | **置信度**：High
- **前置**：AutoConfigurationImportFilter/ImportListener/ImportSelector（Boot 自动配置三扩展点）、ClassLoader 缓存、@Import
- **需求**：**动态 JDBC 自动配置的过滤/缓存/排除**——父上下文排除模块自动配置（防抢建）+ 子上下文按需导入——**AutoConfiguration 类名的可见性控制**
- **参考实现**：**ImportSelector 定制**（DynamicJdbcAutoConfigurationImportSelector :27——extends AutoConfigurationImportSelector + DisposableBean（:67 destroy 清缓存——**生命周期清理**））；**过滤**（ImportFilter :25——INSTANCE 单例 :27 + match（:39/:49——按环境/类加载器过滤 AutoConfiguration））；**监听缓存**（ImportListener :26——onAutoConfigurationImportEvent（:35——导入事件时**缓存 AutoConfiguration 类名**）；**Repository 缓存库**（DynamicJdbcAutoConfigurationRepository :30-106——静态 cache（:42/:46——按 ClassLoader 缓存——**ClassLoader 作用域**）+ getAutoConfigurationClassNames（:65——**取全量**）+ **按前缀取**（:81-100——getAutoConfigurationClassNames(context, classPrefixes)——模块前缀过滤——**父上下文排除的类名来源**（KP-603 :182-188 消费））+ clear（:101）+ **LoadingConfiguration 标记类**（:105-106——@EnableDynamicJdbcAutoConfiguration 内部标记——**触发加载的锚点**）；**启用注解**（EnableDynamicJdbcAutoConfiguration :22——@Import(ImportSelector)——子上下文配置类（DynamicJdbcChildContextConfiguration :12 实证——子上下文启用））
- **对比取舍**：**知识增量**：①**AutoConfiguration 三扩展点组合**（Filter 过滤 + Listener 缓存 + Selector 导入——**自动配置的完整控制面**）；②**ClassLoader 作用域缓存**（:42——**多上下文共享类加载器的类名缓存**（防重复扫描））；③**前缀过滤取类名**（:81——**模块级 AutoConfiguration 隔离**（datasource 模块只拿 datasource 相关类名））；④**DisposableBean 清理**（:67——缓存随生命周期销毁）
- **my-xhs**：**该用没用（实证）**——my-xhs 单上下文无 AutoConfiguration 排除需求

### 包: `mybatis` + `mybatisplus`（批 5：12 文件）

#### KP-617 ORM 模块对称族（mybatis 6 文件：MybatisConfigPostProcessor/MybatisConstants/MybatisConfigurationConfigBeanDefinitionRegistrar/MybatisMapperScanConfiguration/MybatisConfigConfigurationPropertiesSynthesizer/MybatisConfigValidator + mybatisplus 6 文件：MybatisPlusConfigPostProcessor/MybatisPlusConstants/MybatisPlusConfigurationConfigBeanDefinitionRegistrar/MybatisPlusMapperScanConfiguration/MybatisPlusConfigConfigurationPropertiesSynthesizer/MybatisPlusConfigValidator——模块化对称实证）

- **维度**：[工程问题]（模块化实现）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（模块化对称） | **置信度**：High
- **前置**：@MapperScan（MyBatis-Spring 官方）、ConfigurationCustomizer（MyBatis Boot）、MybatisProperties/MybatisPlusProperties、占位符常量
- **需求**：**MyBatis/MyBatis-Plus 双 ORM 支持**——每数据库单元独立 SqlSessionFactory + Mapper 扫描（base-packages）+ 类型处理器（REQ-001"不同 ORM 共存"）+ **互斥校验**（REQ-002）
- **参考实现**：**模块对称结构**（mybatis/mybatisplus 各 6 子包同构——config/constants/context/env/validation——**SPI 四族各自实现**（PostProcessor/Validator/Synthesizer/Registrar——模块对称实证））；**Mapper 扫描配置**（MybatisMapperScanConfiguration :35——**@MapperScan(basePackages = 占位符常量)**（:35——DYNAMIC_JDBC_MYBATIS_BASE_PACKAGES_PLACEHOLDER——**注解占位符 + 运行时解析**（扫描包经 Processor 填充后生效））+ **@Bean ConfigurationCustomizer**（:44——customize 中处理 typeEnumsPackage（:53——**base-packages 即枚举包**（mybatis.typeEnumsPackage 缺失时从 base-packages 解析）+ **包扫描**（:60-64——`typeEnumsPackage.contains("*")` 时 CachingMetadataReaderFactory 扫描（:61——**通配符包扫描**（静态资源解析器 + 缓存工厂——RESOURCE_PATTERN_RESOLVER/METADATA_READER_FACTORY 静态单例 :27-30）））；**注册器**（MybatisConfigurationConfigBeanDefinitionRegistrar :19——extends **AbstractScannedConfigurationConfigBeanDefinitionRegistrar**（:20——扫描型注册器——base-packages 包扫描注册 Mapper 配置类 :30）；**合成器**（MybatisConfigConfigurationPropertiesSynthesizer :23-24——base-packages 合成属性（:41-46——`mybatis.base-packages` 键合成）；**校验器互斥**（MybatisConfigValidator :19-21——**doValidate：hasMybatisPlus() 时 addError 互斥错误**（:20-21——"'{}' module and '{}' module must not be present at the same time"——**REQ-002 互斥检查实证**（对称实现：MybatisPlusConfigValidator :20-21 反向校验））；**PostProcessor 空实现**（MybatisPlusConfigPostProcessor :19——`{}`——**无默认值处理**（两 ORM 模块无需填充——模块差异实证））
- **对比取舍**：**知识增量**：①**模块对称架构**（mybatis/mybatisplus 六子包镜像——**SPI 管道的可扩展性实证**（新增 ORM = 复制结构实现四族））；②**@MapperScan 占位符模式**（:35——注解属性用常量占位符——**注解与运行时解耦**（配置类注册后占位符已被替换——扫描包动态））；③**枚举包从 base-packages 推导**（:53——**配置复用**（少一个配置键））；④**通配符包扫描**（:60-64——类型扫描用 Spring 元数据工厂（CachingMetadataReaderFactory）——**扫描性能**（缓存复用））；⑤**互斥校验双向**（:20-21——两校验器互相检查对方存在——**对称防御**（防重复 SqlSessionFactory））
- **测试佐证**：MybatisConfigValidator 断言（互斥错误）——[补扫 test 断言行号]
- **my-xhs**：**已用（实证）**——my-xhs 用 MyBatis-Plus（mybatis-plus 依赖）+ 单 SqlSessionFactory（无多 ORM 共存——单数据源场景无互斥需求）；多 ORM 隔离为 my-xhs 差距（若未来多库）

### 包: `shardingsphere` + `transaction` + `util`（批 6：15 文件）

#### KP-618 ShardingSphere 模块（ShardingSphereConfigPostProcessor:14-17 + ShardingSphereConstants + ShardingSphereConfigurationConfigBeanDefinitionRegistrar:32-91 + ShardingSphereShutdownHookThreadFilter:32-39 + ShardingSphereConfigConfigurationPropertiesSynthesizer:33-106 + ShardingSphereConfigValidator:17-25）

- **维度**：[工程问题]（生态集成）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（YAML→Bean 化） | **置信度**：High
- **前置**：ShardingSphere YamlRootConfiguration/YamlModeConfiguration/YamlRuleConfiguration、ResourceLoaderAware、线程名过滤
- **需求**：**ShardingSphere 集成**——configResource（YAML 文件）→ 解析 → Mode/Rule 配置 Bean 注册 + 关闭钩子线程同步（REQ-001"ShardingSphere 原生支持"）
- **参考实现**：**YAML → 配置 Bean**（ShardingSphereConfigurationConfigBeanDefinitionRegistrar :32-91——extends AbstractConfigurationConfigBeanDefinitionRegistrar + ResourceLoaderAware——**YamlRootConfiguration 解析**（:57-58——yaml 资源 → 根配置对象）+ **Mode 配置注册**（:66——beanName = prefix + modeType + "." + type——**命名空间化 Bean 名**）+ **Rule 配置注册**（:86——prefix + 规则类短名——**每规则一 Bean**）；**关闭钩子线程过滤**（ShardingSphereShutdownHookThreadFilter :32-39——implements Predicate\<Thread>（:32——**线程名匹配**（:39——供 KP-613 同步执行））；**属性合成**（Synthesizer :33-106——dataSourceNames 逗号合成（:54-58）+ **数据源属性前缀化**（:76-103——`spring.shardingsphere.datasource.{name}.{prop}` 键合成——**每数据源独立前缀**）+ 默认值补全（:85-99——datasource type 推导）；**校验**（Validator :17-25——configResource 存在性校验（:24-25））；**PostProcessor**（:14-17——模块默认值处理）
- **my-xhs**：**已用（实证 2026-08-13——判定修正）**——`my-xhs-order/.../config/ShardingSphereDataSourceConfig.java:34-118`（dataSource :38-50 + resolveWorkerId :90-117——**订单模块分库分表实证**）——此前"my-xhs 未用分库分表"判定错误（铁律 #6 再验证价值）；**差异**：my-xhs 用 ShardingSphere 官方 Starter 配置类（无 YAML→Bean 化管道——直接 @Bean DataSource）——microsphere 的 YAML→Bean 化机制 my-xhs 不需要（无 JSON 配置模型）

#### KP-619 Transaction 模块（TransactionConfigPostProcessor:14-17 + TransactionConfigurationConfigBeanDefinitionRegistrar:20-42 + PlatformTransactionManagerBeanNameGenerator:14-17 + TransactionConfigConfigurationPropertiesSynthesizer:17-26 + TransactionConfigValidator:16-33）

- **维度**：[工程问题]（事务管理）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：PlatformTransactionManager/PlatformTransactionManagerCustomizer、Bean 别名注册、@Qualifier 注入
- **需求**：**每单元独立事务管理器 + 别名注入**——子上下文的 TransactionManager 注册别名（用户配置 name）→ 父上下文可按名 @Qualifier 注入（REQ-001"TransactionManager order-tx"）
- **参考实现**：**别名注册**（TransactionConfigurationConfigBeanDefinitionRegistrar :30-34——registerAlias——`registerBeanAliases(PlatformTransactionManager.class, aliases)`（:34——**按类型 + 别名列表注册**（事务管理器多别名——`transaction.name` 配置的别名）——**父上下文按名注入通道**）；**customizers 注册**（:38-42——PlatformTransactionManagerCustomizer 配置类注册）；**Bean 名生成器**（PlatformTransactionManagerBeanNameGenerator :14-17——ParentContextBeanNameGenerator\<PlatformTransactionManager> 实现——**事务管理器专属命名**（KP-604 消费））；**校验**（Validator :19-33——validateName（:19——name 必填校验）+ **customizers 组件类型校验**（:32-34——validateComponentType(customizers, PlatformTransactionManagerCustomizer.class)——**配置类名合法性**）；**合成器**（Synthesizer :17-26——事务属性合成）
- **对比取舍**：**知识增量**：①**事务管理器别名机制**（:30-34——按类型 + 别名注册——**父上下文 @Qualifier(name) 注入子上下文事务**（跨上下文按名引用——子上下文暴露的标准路径）；②**Bean 名生成 SPI 的模块实现**（:14-17——KP-604 生成器族的 transaction 版）
- **my-xhs**：**已用（实证）**——my-xhs 单事务管理器（@EnableTransactionManagement 默认）；多事务隔离为差距（若多库）

#### KP-620 util 族（DynamicJdbcConfigUtils:54-165 + DynamicJdbcPropertyUtils + DynamicJdbcUtils + FunctionUtils + URLUtils）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：Environment 属性扫描、Jackson 解析、类路径资源
- **需求**：**配置读取与克隆工具**——JSON 配置收集/解析/克隆 + 属性工具
- **参考实现**：**配置收集**（DynamicJdbcConfigUtils.getDynamicJdbcConfigs :69——**前缀扫描 Environment 收集全部 config 属性**（:70/:85——`microsphere.dynamic.jdbc.configs.*` 属性名遍历））；**JSON 解析**（getDynamicJdbcConfig :98-119——属性值 = JSON 内容（:121——`environment.getProperty(propertyName)`——**配置即属性**（JSON 字符串存属性——Nacos 等配置中心可直接下发）+ parseDynamicJdbcConfig（:146-159——ObjectMapper 解析 + 格式错误抛 IllegalArgumentException（:151——**友好错误信息**））+ **readResourceContent**（:135——**classpath 资源路径支持**（属性值可以是资源路径——`classpath:xxx.json`））；**克隆**（cloneDynamicJdbcConfig :160-165——**JSON 序列化往返克隆**（:161——对象 → JSON → 对象——深克隆通用方案——防引用共享）
- **my-xhs**：**该用没用（实证）**——my-xhs @ConfigurationProperties 直绑（无 JSON 配置工具族）；JSON 往返克隆模式通用可借鉴

### 包总结（shardingsphere + transaction + util 批 6）

- **核心命题**：**"生态集成模块 + 事务暴露 + 配置工具"**——shardingsphere（YAML→Bean 化 + 关闭同步）、transaction（别名注入 + 命名生成器）、util（JSON 配置收集/解析/克隆）
- **缺陷认知**：无跨单元事务协调（REQ 缺陷表实证——每单元独立 TransactionManager——跨单元无事务保证）

### 包总结（mybatis + mybatisplus 批 5）

- **核心命题**：**"SPI 管道的模块对称实现"**——双 ORM 各自六子包镜像实现四族 SPI——扩展性 + 互斥校验
- REQ-002 证实：互斥检查（:20-21）——"mybatis + mybatis-plus 互斥"
- 与 baomidou 对比：baomidou 共享 SqlSessionFactory（不支持多 ORM）——微球每单元独立（REQ 表实证）

### 包总结（datasource + autoconfigure 批 4）

- **核心命题**：**"事件驱动重建的完整链路终点 + 自动配置控制面"**——DynamicDataSource（delegate 交换 + 延迟关闭 + 事件消费）是仓库的核心价值点；Import 三件套 + Repository 保证多上下文场景的 AutoConfiguration 秩序
- 与 baomidou 本质差异再实证：delegate 交换（连接池替换）vs ThreadLocal 路由
- my-xhs 对照：DynamicDataSource 同名类存在（原生监听 ZoneContext——无子上下文重建——简化取舍）

### 包总结（context 注册器族 + env 批 3）

- **核心命题**：**"注册与合成的模块化管道"**——4 SPI 的第 3/4 阶段基座族（注册器 supports 门控 + 合成器前缀反射）+ 环境配套（默认属性/关闭同步）
- 设计一致：全部基类四 Aware 注入（PostProcessor/Validator/Synthesizer/Registrar 四族同构——**统一基座风格**）
- 关闭时序设计独特：Thread::run 同步化（:46）——子上下文关闭前保证 sharding 清理

### 包总结（config 批 2）

- **核心命题**：**"配置模型 + 四类 SPI 基座"**——DynamicJdbcConfig（模块字段 + Zone 感知 + 派生属性）→ ConfigurationCapable（泛型反射）→ 四类处理器基类（Aware 注入 + 模板方法）→ 常量约定
- 生态呼应：resolveActualTypeArgumentClass（02 仓库泛型模型）、ZoneContext（06 仓库）、FunctionUtils.setValueIfAbsent（01 仓库）
- ha-datasource 再实证：DynamicJdbcConstants.HIGH_AVAILABILITY_DATASOURCE_MODULE = "ha-datasource"（:13——JSON 键名）

### 包总结（context 批 1）

- **核心命题**：**"子上下文隔离 + 事件驱动联动"**——启动装配（ApplicationPreparedEvent）→ 每单元子上下文（DynamicJdbcChildContext）→ 4 SPI 管道（Processor）→ 刷新回注父上下文（RefreshedListener）→ 变更传播（Zone/配置 → DynamicJdbcConfigChangedEvent → DynamicDataSource）
- **跨仓库实证**：PropagatingDynamicJdbcConfigChangedEventListener 消费 **06 仓库 ZoneContextChangedEvent**（:50/:58）——多活 Zone 切换 → 存储侧联动链路完整
- **ha-datasource 修正**：历史 REQ-004 声称的 ha-datasource 不是独立包——是 DynamicJdbcConfig 的 highAvailabilityDataSource 字段（Processor :116 + Propagating :80 实证）
- **缺陷候选**：忙等轮询启动（:140-150）、zone 属性名字符串耦合（:91）、config 原地清空副作用（:112-119）

---

## 三、深度 review 七项报告（批 1-6 合并）

> 2026-08-13 批判性 review：穷尽性核对先行——81/81 生产文件全覆盖（context 18 + config 9+constants 1 + env 7 + datasource 7 + autoconfigure 5 + mybatis 6 + mybatisplus 6 + shardingsphere 5 + transaction 5 + util 5 + config/validation 6 + config/annotation 1）。

- [x] **① 源码行号精确核对**：KP-601:92-107/:60-68、KP-602:50-71/:112-119/:150-166、KP-603:78-89/:116-160/:178-189、KP-604:70-74/:97-110/:117-124、KP-605:49-50/:63-72/:74-85/:87-97、KP-614:82-94/:174-201/:216-235/:238-251/:280-327、KP-617:35/:44-64、KP-618:57-86、KP-619:30-34、KP-620:69-165——全部 grep 实证 ✓
- [x] **② 穷尽性**：81/81 生产文件全覆盖（脚本核对 basename 逐文件 grep——**曾漏 27 个（批 5/6 归组未列全文件名）→ 已补全**——铁律 #1 执行）✓
- [x] **③ 空节标注**：mybatisplus config 空实现（KP-617——PostProcessor {} 实证）✓
- [x] **④ 过时三级**：8 KP 全部标注（无过时项——子上下文/管道/事件重建均时间无关模式；shardingsphere/mybatis 依赖外部生态版本）✓
- [x] **⑤ 重复内容**：DynamicDataSource 双实现（microsphere 重建 vs my-xhs 预建池——KP-614 对照）；Propagating 与 06 仓库 ZoneContextChangedEvent（KP-605 跨仓库关联）；ha-datasource 三处（Constants :13/Config 字段 :73-74/Processor :116）✓
- [x] **⑤b 引用目标核对**：AnnotationConfigApplicationContext/ConfigurationPropertySources/AnnotationConfigUtils/AutoConfigurationImportFilter 等官方类（spring-framework/spring-boot 本地源码有）✓；DatabaseDriver（spring-boot 本地有）✓；baomidou dynamic-datasource 无本地源码——对照断言基于领域知识（置信度 Medium 标注）✓
- [x] **⑥ 诚实标注**：批 5/6 部分基类未逐行深读（AbstractModuleConfigPostProcessor 等——归组标注）；baomidou 对照无源码实证已标注 ✓
- [x] **⑦ 命名空间迁移**：N/A ✓

### 深度 review 新发现（逻辑证伪轮）

| # | 发现 | 源码实证 | 置信度 |
|---|------|---------|--------|
| R1 | **启动忙等轮询**：processDynamicJdbcChildContexts 的 awaitTermination 循环（:140-150）——completedTaskCount==parallelism 才 break——若某子上下文初始化死锁（如数据库不可达且超时未设）**启动永久挂起**（无总超时） | DynamicJdbcContextApplicationListener:140-150 | Medium |
| R2 | **config 原地清空副作用**：processDynamicDataSource 清空 datasource/ha/sharding 字段（:112-119）——**对调用方可见的破坏性修改**（重建事件 Propagating :79 每次从 Environment 重新解析——**有补救**（副作用影响有限）） | DynamicJdbcContextProcessor:112-119 | Medium |
| R3 | **zone 属性名字符串硬编码**：`"zone".equals(propertyChangeEvent.getPropertyName())`（:91）——未用常量——ZoneContext 属性改名则静默失效 | PropagatingDynamicJdbcConfigChangedEventListener:91 | High |

### 测试扫描记录（02 §2.1，代表性测试全扫）

| 测试文件 | 验证了 | 结论 |
|---------|--------|------|
| DynamicJdbcContextApplicationListenerTest | **真实数据库端到端**（AbstractMariaDB4jTest 基类）：shardingJdbcConfigs 1/primaryConfig 名/dataSourceMap 1/**unwrap ShardingSphereDataSource**（:70/:86——delegate 链）/mappers 4 | KP-603/614 ✓ |
| DynamicJdbcContextApplicationListenerMultipleContextTest | 多配置：3 configs + mappers 12 + transactionManagers（myTransaction :68）——**多子上下文并存实证** | KP-603 ✓ |
| DynamicJdbcConfigTest | JSON 绑定 + has* 辅助族全断言（:48-63） | KP-607 ✓ |
| ConfigurationPropertiesFlatterTest | **数组下标扁平化**（:33-36——datasource[0].name/datasource[1].name/type） | KP-612 ✓ |
| DataSourceModulePropertiesTest | bannedModules 绑定（:43-54） | KP-615 ✓ |
| AbstractConfigPostProcessorTest | Aware 注入断言（:35-39——environment/classLoader/context assertSame） | KP-608 ✓ |
| ShardingSphereShardingDatabases/Tables/ReadWriteSplittingTest | 真实分库分表/读写分离集成 | KP-618 ✓ |
| SingleDataSourceTest / util 3 测试 | 单数据源端到端 / 配置工具 | KP-614/620 ✓ |

### 历史 REQ 交叉验证清单（07-dynamic 完整版）

> 来源：`microsphere-analysis/18-microsphere-dynamic-analysis/18-REQ-requirements-spec.md`（REQ-001~005 + 缺陷）+ 14 篇分析
> 状态：✅ 已验证补入 KP / ⬜ 待验证

| # | 历史断言 | 验证 | 落点 |
|---|---------|------|------|
| REQ-001 | JSON → 子上下文自动装配（每单元独立 DataSource/SqlSessionFactory/TransactionManager） | ✅ 证实（DynamicJdbcChildContext :30 + ContextApplicationListener :78-89 + 模块注册器族） | KP-601/611 |
| REQ-002 | 4 SPI × 6 模块管道（PostProcessor/Validator/Synthesizer/Registrar） | ✅ 证实（Processor :50-71 五步管道 + loadFactories 四族 :218-246 + 模块对称六子包） | KP-602/611/612 |
| REQ-002-互斥 | mybatis + mybatis-plus 互斥检查 | ✅ 证实（MybatisConfigValidator :20-21 + MybatisPlusConfigValidator :20-21 双向） | KP-617 |
| REQ-003 | 独立 Bean 生命周期子上下文（Environment merge + 级联关闭） | ✅ 证实（mergeParentEnvironment :60-68 + 父关闭级联 :70-74） | KP-601/604 |
| REQ-004 | ha-datasource 按 zone 分组 + Zone 切换重建 | ✅ 证实（Config 字段 highAvailabilityDataSource :73-74 + **ha-datasource 是配置字段非独立包（历史 REQ 表述修正）** + Propagating :80 仅 HA 触发） | KP-607/605 |
| REQ-005 | 配置热更新（PropertySourcesChangedEvent → 重建） | ✅ 证实（Propagating :56-72 + DynamicDataSource Refreshing 监听 :303-327 重建） | KP-605/614 |
| 缺陷-跨单元事务 | 无跨数据源事务协调 | ✅ 证实（每单元独立 TransactionManager——REQ 缺陷表 + 批 6 实证） | KP-619 |
| REQ-001-驱动 | URL scheme → driverClassName 推导（DatabaseDriver） | ✅ 证实（DataSourcePropertiesConfigPostProcessor :119-122） | KP-615 |

**验证成果**：REQ 5 项 + 互斥 + 缺陷全部证实；ha-datasource 表述修正（字段非包）。

---

## 四、my-xhs 落地判定汇总表（2026-08-13 实证版）

> 判定均经 my-xhs 代码实证（铁律 #6——先查 data-workspace-my-xhs 索引再写）。

| KP | 判定 | 说明 |
|----|------|------|
| KP-601 | 该用没用 | 子上下文隔离——my-xhs 单上下文（无多单元） |
| KP-602 | 该用没用 | 4 SPI 管道——my-xhs 无模块化 |
| KP-603 | 该用没用 | 多单元并行装配——my-xhs 无 |
| KP-604 | 该用没用 | Bean 回注——my-xhs 无跨上下文 |
| KP-605 | 已用（简化） | 事件消费：my-xhs 原生 PropertyChangeListener（无传播层） |
| KP-606 | 不该用 | 配套载体 |
| KP-607 | 该用没用 | JSON 配置模型——my-xhs 用 @ConfigurationProperties |
| KP-608 | 不该用 | 泛型反射技巧 |
| KP-609 | 该用没用 | 校验管道——my-xhs 无模块化校验 |
| KP-610 | 不该用 | 常量 |
| KP-611 | 该用没用 | 模块注册器 |
| KP-612 | 该用没用 | 属性合成器 |
| KP-613 | 该用没用 | 关闭钩子同步（my-xhs 无 sharding） |
| KP-614 | **已用（架构差异）** | my-xhs DynamicDataSource 同名但**预建池切换架构**（Map<zone,DataSource> + 连接计数等待）vs microsphere 子上下文重建——两种动态数据源架构 |
| KP-615 | 已用 | DatabaseDriver 推导（Boot 官方能力共享） |
| KP-616 | 该用没用 | AutoConfiguration 拦截族（单上下文无排除需求） |
| KP-617 | 已用 | MyBatis-Plus 单 ORM（多 ORM 互斥/隔离为差距） |
| KP-618 | **已用（修正）** | my-xhs-order ShardingSphereDataSourceConfig:34-118（订单分库分表实证——官方 Starter 配置类，无 YAML→Bean 管道） |
| KP-619 | 已用 | 单事务管理器（多事务隔离为差距） |
| KP-620 | 该用没用 | JSON 配置工具——my-xhs @ConfigurationProperties 直接绑定 |

**汇总**：已用 5 / 该用没用 13 / 不该用 3。
**核心差距**：my-xhs 单数据源场景——多单元隔离/4 SPI 管道/ShardingSphere 均未用；**架构差异实证**：my-xhs DynamicDataSource 预建池切换 vs microsphere 子上下文重建（本仓库最大对照价值）。
**关联**：my-xhs `zone/datasource/DynamicDataSource`（403 行）与 06 仓库 zone 机制联动（ZoneContext 变更 → 换池）——**多活存储侧闭环实证**。

### 包: 问题域知识补充（深度 review 轮——对照历史 14 篇分析补提取）

#### KP-621 动态数据源三种机制知识图谱（AbstractRoutingDataSource vs 子上下文重建 vs 预建池切换）

- **维度**：[分布式问题]（数据源路由）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：AbstractRoutingDataSource（Spring 官方——本地源码实证）、ThreadLocal、DataSource 包装链
- **需求**：**动态数据源的机制分类**——"请求级路由" vs "对象级热替换" vs "池级切换"——三种本质不同的架构（历史 18-06 对照 + 本仓库 + my-xhs 三方实证）
- **参考实现**：**机制 A——AbstractRoutingDataSource 路由**（Spring 官方：`determineCurrentLookupKey()`（AbstractRoutingDataSource.java 本地实证 :43/:48——targetDataSources 映射 + getConnection 按 key 查池 + 默认兜底）——**请求级路由**（同一 DataSource 对象内部按 key 分派——baomidou @DS + ThreadLocal 即此机制（历史 18-06 实证）——**my-xhs ReadWriteRoutingDataSourceConfig 实证也是此机制**（@Primary + ReadWriteRoutingDataSource extends AbstractRoutingDataSource + DataSourceType.MASTER/SLAVE——**静态主从路由**（读写分离）——my-xhs 的 zone 切换（预建池）与读写分离（路由）是两套并存机制））；**机制 B——delegate 热替换**（本仓库 DynamicDataSource——**对象级替换**（整个 DataSource 引用被换——新子上下文 + 延迟关闭——连接池级隔离））；**机制 C——预建池 Map 切换**（my-xhs zone/datasource/DynamicDataSource——**池级切换**（Map<zone,DataSource> 查找 + 连接计数等待——已有池之间切））
- **对比取舍**：**知识增量**：①**三种机制的维度划分**（路由粒度：连接请求级（A）/对象引用级（B）/池集合级（C）——**隔离强度与开销排序**（A 轻共享 → B 重建隔离 → C 预建切换））；②**适用场景**（A：读写分离/多租户轻路由；B：配置热更新/多活 Zone 切换（完整生命周期重建）；C：有限 zone 集的快速切换（HA 主备））；③**my-xhs 双机制并存实证**（读写分离用 A（AbstractRoutingDataSource）、Zone 切换用 C（预建池）——**机制组合选型**（按场景混用））；④**HikariCP 生态**（本地源码实证 ConcurrentBag——高性能连接容器（handoff 队列——历史 18-06 :270 详述）——连接生命周期/验证/调优参数（:368-404——经验配置值）——**连接池知识本体**（独立于动态机制的生态知识）
- **my-xhs**：**已用（实证）**——ReadWriteRoutingDataSourceConfig（A 机制）+ zone/datasource/DynamicDataSource（C 机制）——双机制并存；B 机制（子上下文重建）未用（无多单元）

#### KP-622 并发设计模型（8 并发场景 + volatile/mutex/ConcurrentHashMap/调度器）

- **维度**：[工程问题]（并发设计）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：JMM 可见性（volatile）、synchronized 临界区、ConcurrentHashMap、ScheduledExecutorService
- **需求**：**动态数据源场景的并发正确性**——8 个并发场景的系统化设计（历史 18-07 汇总）
- **参考实现**：**8 场景映射**（历史 18-07 :16-32 实证——①热替换可见性（DynamicDataSource delegate——**volatile + synchronized(mutex) 交换**（:186——读无锁写互斥）②子上下文并行创建（线程池——InitializeErrors ConcurrentHashMap + putIfAbsent :16/:20）③延迟关闭协调（closeScheduler 单线程调度 :77——延迟关闭与 destroy 协调（shutdownScheduler :266-270）④事件并发（Propagating——SmartApplicationListener 同步分发——无共享状态）⑤AutoConfiguration 缓存并发（Repository——ClassLoader 作用域 Map）⑥防重复处理（OnceApplicationPreparedEventListener——主上下文只处理一次）⑦Bean 升迁并发（RefreshedListener——父上下文多子上下文同时注册——registerBean 幂等性）⑧错误收集（InitializeErrors——ConcurrentHashMap 首错保留））；**JMM 语义**（volatile 的 happens-before——delegate 写后读可见（历史 18-07 :33-108 详述——可见性问题的完整推导）+ volatile + synchronized 配合（:108——**写临界区 + 读 volatile**——锁范围控制））
- **对比取舍**：**知识增量**：①**并发场景清单化**（8 场景——**并发设计的系统化方法**（先列场景再选机制）——面试/设计模板）；②**volatile + mutex 读写分离**（读多写少场景的经典组合——可见性 + 原子性分工）；③**ConcurrentHashMap + putIfAbsent 首错保留**（:20——**错误聚合的并发语义**（第一个错误优先——不覆盖后续））；④**调度器生命周期**（单线程调度器——延迟任务与销毁协调）
- **my-xhs**：**已用（实证）**——my-xhs DynamicDataSource activeConnectionCount（AtomicInteger）+ 切换等待——并发正确性同族设计（连接计数等待 vs 延迟关闭——两种在途连接治理）

### 包总结（问题域知识补充）

- **核心命题**：**"动态数据源机制分类 + 并发设计模型"**——三种机制（路由/重建/预建池）+ 8 并发场景——对照历史 18-06/18-07 的知识本体提炼
- 生态实证：AbstractRoutingDataSource（spring-framework 本地源码）、HikariCP ConcurrentBag（本地源码）——替代物/对照物全部本地实证
