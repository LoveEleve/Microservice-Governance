# microsphere-java 知识点提取

> 源码：`/data/workspace/java-training-camp/cloud-native-code/share/microsphere-java`（依赖链第 2 站，confucius-commons 之后）
> 提取时间：2026-08-12（批 1-7：core 主要包；批 8：classloading 16 + lang 8 + net 13 + management 4 + metadata 9 + json 8 + concurrent 4 + invoke 2 + misc/nio/security/text 4 + filter 9）
> 状态：批 1-8 已提取 + 拆提修正（KP-124→4 方向族 / KP-145→3 / KP-146→3——46→53 KP）；MCP 索引已建（13165 节点/49189 边）
> 关联：与 confucius-commons 同作者（mercyblitz）——工具类主题重叠时交叉引用（06 纪律）

## 一、仓库定位

**microsphere 生态的标准库**（Foundation）——confucius-commons 的继任/扩展：字符串/集合/反射/类型转换/事件/类加载/并发/配置属性的全套 JDK 工具 + 注解 + 编译期处理器。README 实证 Features 12 项（:20-32）。核心维度：[工程问题]（工具/扩展机制）+ [规范]（注解/SPI）。

## 前置条件清单

读者需先掌握：1. confucius-commons 全部知识点（本仓库是其扩展——JDK 工具主题重叠）2. 注解机制（Retention/Target/注解处理器）3. JSR-305 空注解 4. Maven 多模块
未达前置者，先补：confucius-commons outline（`outline/01-confucius-commons-jdk知识大纲.md`）

## 掌握度

目标读者：中级偏上（读源码多、Spring 熟——HANDOVER 画像）
讲解策略：与 confucius-commons 交叉引用，不重复提取（06 纪律）

---

## 二、逐文件映射 + 原子记录

### 模块: `microsphere-java-annotations`（包 `io.microsphere.annotation`）

#### KP-101 `ConfigurationProperty` 配置属性注解（ConfigurationProperty.java:38-97）

- **维度**：[规范] | **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式]（注解元模型） | **置信度**：High
- **前置**：注解元模型（name/type/defaultValue 设计）、配置属性概念
- **需求**：**声明式配置绑定**——在类字段上标注配置属性来源/名称/默认值，编译期生成元数据（配合 annotation-processor 模块）
- **自主实现**：注解六要素——`name`（:60 属性名）、`type`（:67 默认 String）、`defaultValue`（:74）、`required`（:81）、`description`（:88）、`source`（:95 来源数组）+ 三来源常量（:43/:48/:53 system-properties/environment-variables/application）
- **参考实现**：confucius/spring 无此物——这是 microsphere 自家设计的**注解驱动配置元模型**；与 Spring `@ConfigurationProperties` 对照：Spring 靠类名+字段名推断 + `@Value("${key}")`，microsphere 是**显式元模型**（name/type/defaultValue/source 全声明）
- **对比取舍**：**设计决策**——显式元模型 vs Spring 约定推断：microsphere 牺牲简洁换确定性（编译期可校验）；source 数组支持多来源优先级（:95）
- **测试佐证**：ConfigurationPropertyTest 存在（annotations 测试目录）
- **my-xhs**：**该用没用**——my-xhs 用 Spring `@ConfigurationProperties` 绑定；microsphere 的**显式元模型+多来源设计**可作为 my-xhs 自定义配置注解的参考（若需多环境来源优先级）

#### KP-102 空注解双包：`@Nonnull/@Nullable`（Nonnull.java:32-37 + Nullable.java:34-39）

- **维度**：[规范] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[过时→JSpecify/Spring Nullable]（JSR-305 底层）+ [时间无关模式]（空注解语义） | **置信度**：High
- **前置**：JSR-305（confucius KP-12）、TypeQualifierNickname 机制
- **需求**：空值契约标注——**基于 JSR-305 的类型限定器昵称**（TypeQualifierNickname）
- **自主实现**：直接 Spring `org.springframework.lang.Nullable` 或 JSpecify
- **参考实现**：microsphere 用 `@TypeQualifierNickname`（:35/:37）包装 `@javax.annotation.Nonnull`（:34）——**TypeQualifierNickname 是 JSR-305 的"注解组合"机制**（带 when=MAYBE :36 表达可空）
- **对比取舍**：**知识增量：TypeQualifierNickname**——JSR-305 的复合注解机制（组合注解+元注解传播）；但 JSR-305 停维护（同 confucius KP-12），Spring Nullable/JSpecify 是替代
- **my-xhs**：**不该用**——Spring 生态直接用 Spring Nullable（confucius KP-12 同判定）

#### KP-103 线程安全标注：`@ThreadSafe/@NotThreadSafe`（ThreadSafe.java:26-30 + NotThreadSafe.java:25-29）

- **维度**：[规范] | **权重**：[边缘] | **深度**：🟢 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：并发语义标注概念
- **需求**：类级线程安全契约标注——文档性质
- **参考实现**：**注意细节**：①头部版权注释实证 **Brian Goetz（JCIP 作者）2005 版权**（ThreadSafe.java:1-6）——**这是 Java Concurrency in Practice 附带的注解移植**（不是 JSR-305！同 KP-35 的"JDK 移植"模式）；②`javax.annotation.concurrent.NotThreadSafe` 是 **JCIP 原始包**（非 JSR-305）——confucius 时代误判过同类；③**互相 import 对方**（ThreadSafe.java:9 import javax NotThreadSafe / NotThreadSafe.java:9 import javax ThreadSafe）——**未使用的 import**（各自只定义自己的注解，注释掉后残留）
- **对比取舍**：与 JCIP 原始注解重复定义（只是改了包名）；空注解无元数据价值；**移植残留**（未用 import）是质量瑕疵
- **my-xhs**：**不该用**——JCIP 注解/JSR-305 即可

#### KP-104 元注解三件：`@Experimental/@Immutable/@Since`（Experimental.java:36-38 / Immutable.java:31-34 / Since.java:42-68）

- **维度**：[规范] | **权重**：[边缘] | **深度**：🟢 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：Medium
- **前置**：注解 Target/Retention 语义
- **需求**：API 成熟度/不变性/引入版本标注——库作者文档工具
- **参考实现**：@Experimental Target 多元素（ANNOTATION_TYPE/CONSTRUCTOR/FIELD/METHOD/TYPE :23-27）+ Retention SOURCE；@Immutable RUNTIME（:33）；**@Since 有属性结构**——`module()`（:61 模块名，测试实证 "microsphere-java-core"）+ `value()`（:66 版本号，测试实证 "1.0.0"），Target 全元素（:43-54 含 TYPE_USE/TYPE_PARAMETER/PACKAGE）
- **测试佐证**：SinceTest:36-37 实证 `since.module()=="microsphere-java-core"` + `since.value()=="1.0.0"`；ImmutableTest:36/NonnullTest:36/NullableTest:36 实证 getAnnotation 非空；ExperimentalTest:35 实证 SOURCE retention（getAnnotation 返回 null——测试类未标注故 null，弱断言）
- **my-xhs**：**不该用**——内部库无需成熟度标注

---

## 三、批 1 总结（annotations 模块）

- **主题**：microsphere 自研注解集——**配置元模型（核心）+ 空注解包装 + 线程安全/成熟度标注**
- **最高价值 KP**：KP-101（ConfigurationProperty 显式元模型 vs Spring 约定推断的设计对照——"该用没用"候选）+ KP-102（TypeQualifierNickname 是 confucius 未触及的 JSR-305 新机制）
- **与 confucius-commons 关系**：空注解/常量主题重叠 → 交叉引用（KP-102 ↔ confucius KP-12）
- **来源标注教训**：ThreadSafe/NotThreadSafe 是 **JCIP（Brian Goetz）移植**（版权头实证）——非 JSR-305；未用 import 残留
- **测试已扫**：6 个测试全部断言实证（批 1 完成）

## 四、my-xhs 判定汇总（批 1）

| KP | 判定 | 理由 |
|----|------|------|
| KP-101 | **该用没用** | Spring @ConfigurationProperties 够用；但显式元模型+多来源设计值得参考（设计知识保留） |
| KP-102~104 | **不该用** | Spring Nullable 覆盖；冗余/文档性质 |

## 五、待验证（批 2）

- ConfigurationProperty 与 annotation-processor 的联动（编译期生成什么）
- JSR-305 TypeQualifierNickname 在 Spring/生态的现代等价物

### 包: `io.microsphere.constants`（8 文件）

#### KP-105 常量接口家族（Constants.java:13-16 + 7 子接口）

- **维度**：[工程问题] | **权重**：[边缘] | **深度**：🟢 | **优先级**：P3 | **过时**：[过时→JDK 内置常量/枚举（confucius KP-11 同判定：常量接口是反模式）] | **置信度**：High
- **前置**：接口继承、常量反模式（confucius KP-11）
- **需求**：共享常量组织——**微改进**：`Constants` 聚合接口 extends 6 个子接口（FileConstants/PathConstants/PropertyConstants/ProtocolConstants/SeparatorConstants/SymbolConstants，:13-14）——**按域分接口再聚合**（confucius 是平铺 5 接口）
- **参考实现**：分层常量接口：文件扩展名（FileConstants:21-66——ZIP/JAR/WAR/EAR/CLASS + 扩展名组合 :56-61）、路径（PathConstants:18-38——char 与 String 双版本 SLASH_CHAR/SLASH）、协议（ProtocolConstants:24-59——file/http/https/ftp + 复用 FileConstants :44-59）、符号（SymbolConstants:30-333——**双字符表** char + String 两套 :184-333）、属性（PropertyConstants:30-35——`microsphere.` 前缀 :35 是生态配置命名空间）、分隔符（SeparatorConstants:26-41——**用 JDK 内置** `File.separator`/`File.pathSeparator`/`System.lineSeparator()` :31-41 静态导入）
- **对比取舍**：**相比 confucius 的关键进步**：①分隔符不再硬编码（confucius KP-11 用 `"/"` 字面量——这里静态导入 `File.separator` :31 跨平台正确）；②char/String 双版本供 API 复用（:18-23）；③ResourceConstants 定义**配置元数据文件约定**（:43-60——`configuration-properties.json` 是 annotation-processor 的产出物——与 KP-101 联动）
- **my-xhs**：**不该用**——常量接口反模式；JDK 内置 + my-xhs 自有常量

### 包: `io.microsphere.beans`（4 文件）

#### KP-106 `BeanMetadata` 属性元数据缓存（BeanMetadata.java:45-53 等）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式]（Introspector 封装） | **置信度**：High
- **前置**：JavaBeans 规范（Introspector/PropertyDescriptor）、反射缓存模式
- **需求**：**属性描述符的缓存封装**——Introspector.getBeanInfo 昂贵（反射），按类缓存 PropertyDescriptor map
- **自主实现**：`Introspector.getBeanInfo` + `ConcurrentHashMap<Class, BeanMetadata>` 缓存——**同构**
- **参考实现**：microsphere 构造时 introspect（:53）+ 内部缓存 `propertyDescriptorsMap`（:51）；`@Immutable` 标注（:20 自注解）
- **对比取舍**：模式时间无关；`@Immutable` 自用注解是**自举**（microsphere 用自己的注解标注自己的类——:20 实证）
- **my-xhs**：**该用没用**——my-xhs 若需高性能反射属性访问（如动态表单/通用导出），可参考此缓存模式；Spring `BeanWrapperImpl` 已有内置缓存（替代）

#### KP-107 `BeanProperty` 属性值包装（BeanProperty.java:37-57 等）

- **维度**：[工程问题] | **权重**：[边缘] | **深度**：🟢 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：PropertyDescriptor、属性读写
- **需求**：属性名/值/类型/描述符的不可变包装——BeanUtils 的中间数据结构
- **参考实现**：四字段（name/value/beanClass/descriptor :40-49）+ 构造校验（@Nonnull :40）；**Null 语义**：name/beanClass/descriptor 必须非空（@Nonnull），value 可空（@Nullable :42）——**空注解使用实证**（KP-102 的落地用法）
- **my-xhs**：不该用——Spring BeanWrapper 覆盖

#### KP-108 `BeanUtils` 属性解析核心（BeanUtils.java:86-148,203-272,449-514 等）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（递归解析设计） | **置信度**：High
- **前置**：JavaBeans 规范、递归、深度限制、属性路径
- **需求**：**Bean → 属性 Map 的递归解析**——嵌套对象/集合/数组展平，带深度限制防栈溢出
- **自主实现**：递归 + `maxResolvedDepth` 深度限制（默认 8——:107-123 配置化）+ Map/List/Set/Array 特判
- **参考实现**：microsphere **配置化深度**——`microsphere.bean.properties.max-resolved-depth` 系统属性（:107-112）+ 缓存大小 `microsphere.bean.metadata.cache.size`（:138-148）+ **BeanMetadata 并发缓存**（:150 `newConcurrentHashMap(BEAN_METADATA_CACHE_SIZE)`）；核心私有实现（:449-514）：**深度检查 `resolvedDepth.incrementAndGet() >= maxResolvedDepth` 返回空 map（:453-456——每次递归先自增深度，达到上限截断）**；类型分派（:483-512——primitive/simple/CharSequence/Number/enum/Class 直接返回 :487-495，array/list/set/queue/enumeration/map 各自转换 :497-512，POJO 递归 :512）
- **对比取舍**：**设计决策值得学**：①**深度限制是防栈溢出的手段而非防环**——`MutableInteger` 自增（:453），A→B→A 循环会在**达到深度上限时截断**（不会死循环，但会消耗完整深度预算——**无 visited 集，深度预算耗尽前会重复解析同对象**，这是与 Jackson/Spring 的差异，它们用对象标识防环）；②系统属性三层（属性名+默认值常量+运行时解析 :107-123）配置化完整模式；③`@Immutable` 返回（:203）；④**可解析类型白名单**（:487-495——Class 也直接返回 :494，避免递归进 Class 元数据）
- **测试佐证**：Javadoc 示例（Person/Address/Country 嵌套 :230-252）——maxResolvedDepth=2 截断嵌套
- **my-xhs**：**该用没用**——my-xhs 通用导出/属性展平场景可用；Spring `BeanMap`/`BeanWrapper` 有内置（替代）；**无 visited 防环需注意**（深度预算替代，性能弱于对象标识防环）

#### KP-109 `beans.ConfigurationProperty` 运行时配置属性（ConfigurationProperty.java:63-252）

- **维度**：[规范] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：KP-101（注解）、元模型概念
- **需求**：**注解的运行时对象化**——@ConfigurationProperty（KP-101 注解）声明的属性在运行时的 Java 对象表示（name/type/value/defaultValue/required/description + Metadata.sources）
- **参考实现**：**注解↔对象双层设计**：注解（KP-101 编译期声明）+ 本类（运行时实例 :63-252）——属性字段（:68-98）+ 嵌套 `Metadata` 类（:219-252，sources 集合 :224）——**注解的元模型在运行时以对象承载**
- **对比取舍**：**知识增量：元模型双层**（编译期注解 + 运行时对象）——Spring 用 `Binder` + `ConfigurationProperties` 对象直接绑定，microsphere 是"注解→对象"显式映射；`equals/hashCode` 含 Metadata（:189/:200——值对象语义完整）
- **my-xhs**：**不该用**——Spring Binder 覆盖；但**元模型双层设计**是自定义配置框架的参考

---

## 六、深度 review 七项报告（批 1-2）

> 2026-08-12 批判性 review：批 1 修正 KP-103（JCIP 移植+未用 import）、KP-104（Since 属性+测试实证）；批 2 写入时验证 + KP-108 防环机制实证 + beans 测试补扫。

### 测试扫描记录（02 §2.1）

| 测试文件 | 验证了 | 结论 |
|---------|--------|------|
| BeanMetadataTest | propertyDescriptors size=9（:60）+ 描述符缓存 same（:54）/BeanInfo 非空（:54） | KP-106 缓存机制 ✓ |
| BeanPropertyTest | 属性包装 | KP-107 ✓ |
| BeanUtilsTest | 缓存 same（:70-71）+ 属性解析 Map（:121-125）+ **null bean 返回空 map（:175）+ 无 readMethod 对象抛 RuntimeException（:181）** | KP-108 边界行为 ✓ |
| ConfigurationPropertyTest（beans） | 属性对象字段 | KP-109 ✓ |
| annotations 6 测试（批 1） | 常量值/注解存在性/Since module+value | KP-101~104 ✓ |
| collection 测试（批 3a） | EmptyIteratorTest（hasNext false/next 抛 NoSuchElement/remove 抛 IllegalState :29-39——**remove 抛 IllegalStateException 非 Unsupported**）、ReadOnlyIteratorTest（:45-59）、DelegatingIteratorTest（:57-71）、ImmutableEntryTest（setValue 抛 UnsupportedOperation :56 + equals/hashCode :61-63）、SingletonIteratorTest（**继承 ReadOnlyIteratorTest 复用测试** :30-35）、EmptyQueueTest（引用 CollectionUtils.emptyQueue :26——**EmptyQueue 在 CollectionUtils 内部非独立类**） | KP-110~114 ✓ |
| CollectionUtilsTest（批 3b） | 判空/工厂断言 + **equals 跨类型相等实证**（List vs Set vs Queue 空集相等 :196-198 + null 对称 :192-195） | KP-116 ✓ |
| ListsTest/MapsTest/MapUtilsTest/SetUtilsTest/ListUtilsTest/QueueUtilsTest | of 工厂 + equals + 专用方法 | KP-117~120 ✓ |
| ConvertersTest / Converter 家族测试 | SPI 加载 + 转换断言（:40-50——**convertIfPossible Date 返回 null 实证**） | KP-121~124 ✓ |
| reflect 测试（批 5） | JavaTypeTest/TypeUtilsTest/MethodUtilsTest 等 | KP-125~129 [待补扫断言] |
| util 测试（批 6） | CompatibleTest（链式 4 条件 on("<")/on("=")/on(">")/on(">=") → get()==">=" :34-41）、AssertTest/VersionTest/VersionOperatorTest/StopWatchTest/ClassLoaderUtilsTest 存在 | KP-130~135 ✓ |
| event/logging/io 测试（批 7） | EventDispatcherTest（:45-46 默认 DIRECT + 监听器数）、DirectEventDispatcherTest/ParallelEventDispatcherTest/ConditionalEventListenerTest、LoggerFactoryTest/JDKLoggerFactoryTest/Sfl4jLoggerFactoryTest 等 | KP-136~141 ✓ |
| classloading/metadata 测试（批 8） | ArtifactDetectorTest（:50 assertNotNull）+ BannedArtifactClassLoadingExecutorTest 等 11 个、metadata 测试 | KP-142/144 ✓ |

### 深度 review 七项

- [x] **① 源码行号精确核对**：批 2——KP-105:13-16/:21-66/:18-38/:24-59/:30-333/:30-35/:26-41/:43-60、KP-106:45-53/:20、KP-107:40-49、KP-108:86-148/:150/:203/:272/:449-514（防环实证 :453-456/:487-495/:512）、KP-109:63-252/:219-252——全部 grep 实证 ✓
- [x] **② 穷尽性**：批 1（8）+ 批 2（constants 8 + beans 4）= 20 文件覆盖；core 剩余 274 文件已按包规划 ✓
- [x] **③ 空节标注**：N/A ✓
- [x] **④ 过时三级**：9 个 KP（101-109）全部标注 ✓
- [x] **⑤ 重复内容**：KP-105 ↔ confucius KP-11（常量接口）交叉引用；KP-102 ↔ confucius KP-12 ✓
- [x] **⑤b 引用目标核对**：无跨文档断言 ✓
- [x] **⑥ 诚实标注**：KP-108 防环机制已实证（深度预算替代 visited 集——无死循环但重复解析）；测试边界（null bean/无 readMethod :175/:181）标注 ✓
- [x] **⑦ 命名空间迁移**：JCIP（javax.annotation.concurrent）非 JSR-305——来源分层已标注 ✓

### 包: `io.microsphere.collection`（批 3a：小文件 24/32）

> 主题：**JDK 集合的"缺失件"补全**——空集合/单例/不可变/委托/适配器家族。JDK9+ `List.of`/`Collections` 已覆盖大半。

#### KP-110 空集合家族（EmptyIterable/EmptyIterator/EmptyDeque + DelegatingIterator 基座）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[过时→`Collections.emptyIterator()`/`emptyList()`（JDK 一直有）+ `List.of`（JDK9）] | **置信度**：High
- **前置**：Iterator/Iterable 契约、空对象模式（Null Object）
- **需求**：**空对象模式**——避免 null 传播的 `emptyIterator()` 调用链（`EmptyIterator.INSTANCE` :63）
- **参考实现**：microsphere 分层——`EmptyIterator extends DelegatingIterator`（:58，**委托给 `Collections.emptyIterator`** :66——复用 JDK 再包一层）、`EmptyIterable extends IterableAdapter`（:44，**传 null 委托** :52-53 + 防御 defaultIfNull）、`EmptyDeque extends AbstractDeque`（:63，单例 :67）
- **对比取舍**：**嵌套委托链**（Empty→Delegating→JDK）是"多一层抽象"——**冗余但教学价值**：展示了空对象 + 委托 + 单例三模式组合；JDK 原生 `Collections.emptyXxx()` 已足够
- **my-xhs**：**不该用**——`Collections.emptyIterator()` 原生

#### KP-111 单例集合家族（SingletonIterator/SingletonEnumeration/SingletonDeque）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P3 | **过时**：[过时→`Collections.singletonList()`/`List.of(e)`（JDK9）] | **置信度**：High
- **前置**：Iterator 有状态遍历（hasNext 翻转）
- **需求**：单元素集合——**经典有状态迭代器实现**（hasNext 标记翻转 :43-58）
- **参考实现**：`SingletonIterator extends ReadOnlyIterator`（:39，**remove 抛异常继承**）+ hasNext 布尔翻转（:43/:50-58）；SingletonEnumeration 同构（:56-71）；SingletonDeque 组合（:69-75 委托 singletonIterator）
- **对比取舍**：**有状态迭代器是经典模式**（时间无关）；JDK9 `List.of` 覆盖
- **my-xhs**：不该用

#### KP-112 不可变/只读家族（ReadOnlyIterator/UnmodifiableIterator/UnmodifiableDeque/UnmodifiableQueue + ImmutableEntry）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[过时→`Collections.unmodifiableXxx()` + JDK9 `List.of`] | **置信度**：High
- **前置**：不可变语义、`@Immutable`（KP-104 应用）
- **需求**：**只读保护**——读操作委托、写操作抛异常
- **参考实现**：`ReadOnlyIterator`（:50——**final remove 抛 IllegalStateException** :53-54）；`UnmodifiableIterator extends ReadOnlyIterator`（:51，hasNext/next/forEachRemaining 委托 :59-70）；`UnmodifiableQueue/Deque`（:65/:60——**所有写方法抛 UnsupportedOperationException** :70-71）；`ImmutableEntry extends DefaultEntry`（:47——**setValue 抛异常** :53-55 + @Immutable 标注）
- **对比取舍**：**知识增量**：两类"只读"语义——**结构只读**（Unmodifiable：抛异常）vs **数据不可变**（Immutable：字段 final）；`@Immutable` 是声明式标注（编译期不可校验，文档性质）
- **my-xhs**：**不该用**——Collections.unmodifiableXxx 原生

#### KP-113 委托家族（DelegatingIterator/DelegatingQueue/DelegatingDeque + DelegatingWrapper 协议）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式]（委托模式） | **置信度**：High
- **前置**：组合优于继承、委托模式、JDBC Wrapper 模式
- **需求**：**组合替代继承**——包装器基座（空/单例/不可变都基于它）
- **参考实现**：`DelegatingIterator implements Iterator, DelegatingWrapper`（:41）；DelegatingQueue（:40，构造非空断言 :45——**assertNotNull + lambda 消息** :45 现代风格）；**DelegatingDeque 用 MethodHandle 缓存 reversed()**（:39——注释自证 "since Java 21" :38，`findPublicVirtual(Deque.class, "reversed")` 是 JDK21 新方法 `SequencedCollection.reversed()` 的反射桥，JDK17 下降级）
- **对比取舍**：**DelegatingWrapper 是解包协议而非标记**（修正前断言）——实证：`DelegatingWrapper extends Wrapper`（DelegatingWrapper.java:78），提供 **`unwrap(Class)`（:88-97）+ `isWrapperFor(Class)`（:100-103）默认方法**——这是 **JDBC Wrapper 模式的移植**（连接/语句解包协议）：`getClass().equals(type)` 直接返回自己（:89-90），否则递归委托（:92-93）；**框架可识别"包装器 vs 真实实现"并可解包**——比纯标记接口高一个层次
- **my-xhs**：**该用没用**——my-xhs 若做缓存/连接包装层（如 Redis/DataSource 包装）可参考 DelegatingWrapper 解包协议（JDBC Wrapper 同款）

#### KP-114 适配器家族（EnumerationIteratorAdapter/IterableAdapter/ArrayEnumeration + ArrayStack）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P3 | **过时**：[过时→JDK9 `Iterable.iterator()` 默认方法/`Enumeration` 已被 Iterator 取代] | **置信度**：High
- **前置**：适配器模式、旧 API 桥接（Enumeration→Iterator）
- **需求**：**遗留 API 桥接**——Enumeration（JDK1.0）→ Iterator（JDK1.2）单向适配
- **参考实现**：`EnumerationIteratorAdapter extends ReadOnlyIterator`（:55——enumeration 委托 :64-70）；`IterableAdapter implements Iterable`（:47——**Iterator→Iterable 反向适配** :56-57 + null 防御 :52）；`ArrayEnumeration`（:51——数组→Enumeration）
- **对比取舍**：适配器模式时间无关；Enumeration 是遗留 API 的教学样本
- **my-xhs**：不该用

#### KP-115 工具类（EnumerationUtils/Iterators/PropertiesUtils.flatProperties + ReversedDeque）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式]（equals/展平逻辑） | **置信度**：High
- **前置**：迭代器语义、属性展平（嵌套 key 规范化）
- **需求**：迭代器相等比较（元素序相等 :41-55）+ 属性嵌套展平（`a.b.c` 规范化）
- **参考实现**：`Iterators.equals`（:41-55——**逐元素比较 + 长度匹配**（:54 双 hasNext 检查）——完整相等语义）；`PropertiesUtils.flatProperties`（:76-96——递归展平 + `normalizePropertyName` 前缀拼接 :95 + @Immutable 返回 :75/:82）；`ReversedDeque.of`（:46-52——**ReversedDeque 幂等**（已反转不再反转 :47-49））
- **对比取舍**：Iterators.equals 是**正确实现**（长度+顺序）；flatProperties 与 Spring `Binder` 展平对照（Spring 用 `.` 分隔路径，microsphere normalizePropertyName 同思路）
- **my-xhs**：**该用没用**——属性展平（KP-115）在 my-xhs 配置转换/日志脱敏场景可用；Spring `Binder` 有内置

### 包: `io.microsphere.collection`（批 3b：大工具类 8/8）

#### KP-116 `CollectionUtils` 工厂/判空/相等工具（CollectionUtils.java:58-687）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[过时→JDK9+ `List.of`/`Collections`] | **置信度**：High
- **前置**：集合 API、空安全设计
- **需求**：集合判空/大小/相等/单例工厂的**空安全统一入口**——23 个静态方法（:58-687）
- **参考实现**：**判空三件套**（isEmpty :58/isNotEmpty :75/toIterable :98）；**转换链**（Collection→Iterable :98 / Iterator→Iterable :121 / Enumeration→Iterator :144 / Enumeration→Iterable :167）；**工厂**（singletonIterable :190 / emptyIterator **直接委托 JDK** `Collections.emptyIterator()` :303-306——不自造！/ emptyIterable 返回缓存 INSTANCE :329-331）；**equals 四判**（:411-450——同引用 :413/双空 :416/长度 :420/containsAll :422）
- **对比取舍**：**设计决策**：①empty 工厂委托 JDK 而非自造（:303）——避免重复实现；②**equals 是"集合语义相等"非"容器相等"**——测试实证跨类型相等（List vs Set vs Queue 空集相等 CollectionUtilsTest:196-198）+ null 对称处理（:192-195）；③containsAll 简化在重复元素场景**不精确**（陷阱范围窄：仅"长度相同 + 元素集相同但频次不同"的 multiset，如 {a,a,b} vs {a,b,b}——长度与 containsAll 都通过但语义不等）——**Set 场景正确、multiset 场景边缘缺陷** [测试无重复元素场景]
- **my-xhs**：**不该用**——JDK `Objects`/`Collections`/Spring `CollectionUtils` 覆盖；**equals 陷阱要注意**

#### KP-117 `Lists.ofList` 重载轰炸 + MethodHandle 工厂（Lists.java:101-533）

- **维度**：[性能优化] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[过时→`List.of`（JDK9 直接可用）] | **置信度**：High
- **前置**：MethodHandle、JDK9 工厂方法、重载设计
- **需求**：**JDK9 前无 `List.of` 的补位**——固定参数工厂
- **参考实现**：**双层设计**：①**重载轰炸**（ofList() :119 / ofList(e1) :152 ... ofList(e1..e9) :444——9 个公开重载避免 varargs 数组分配；另有 **of10MethodHandle**（:95——10 参 handle 已缓存但无对应公开重载 [待验证]））；②**varargs 兜底**（ofList(E...) :513）走 **MethodHandle 反射桥**（ofMethodHandle = `findPublicStatic(List.class, "of", Object[].class)` :101——**运行期探测 JDK9 List.of，存在则 invokeExact，否则降级 Arrays.asList** :519-525）
- **对比取舍**：**知识增量：MethodHandle 版本探测模式**——JDK 演进兼容的标准手法（Sets.of :42-49 / Maps.of :42-49 同款，三文件实证）；**探测-降级双保险实证**（MethodHandlesLookupUtils.findPublicStatic :93-130——方法不存在返回 NOT_FOUND :114-115 + Lookup 失败返回 null :125-129 带 trace 日志）；重载轰炸是 JDK9 前减少数组分配的性能手法（现在 `List.of` 已原生解决）
- **my-xhs**：**不该用**——`List.of` 原生；**MethodHandle 探测模式**是跨 JDK 兼容的参考

#### KP-118 `Sets.ofSet` / `Maps.ofMap` 同模式工厂（Sets.java:42-572 + Maps.java:42-605）

- **维度**：[性能优化] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[过时→`Set.of`/`Map.of`（JDK9）] | **置信度**：High
- **前置**：同 KP-117
- **需求**：Set/Map 固定参数工厂——同 Lists 的 JDK9 补位
- **参考实现**：**多 MethodHandle 按参数数缓存**（Sets:44/:47/:49——of0/of1... 每参数一个 handle；Maps 同构 :44/:47/:49）——比 Lists 更细粒度（按 arity 分派）；Map 还有 **of(Object... keyValuePairs) 交替解析**（Maps:360/:448——键值交替校验）
- **对比取舍**：与 KP-117 同模式；**按 arity 缓存多个 MethodHandle** 是更精细的版本探测（Lists 只缓存 varargs 版）
- **my-xhs**：**不该用**——`Set.of`/`Map.of` 原生

#### KP-119 `MapUtils` 映射工具大全（MapUtils.java:61-1445）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🟡 | **优先级**：P1 | **过时**：[时间无关模式]（工厂/加载因子设计） | **置信度**：High
- **前置**：HashMap 加载因子语义、ConcurrentHashMap、LinkedHashMap accessOrder
- **需求**：Map 全套工具——工厂（newHashMap/newLinkedHashMap/newConcurrentHashMap/newTreeMap）+ 判断（isMap/isEmpty）+ of 工厂
- **参考实现**：**加载因子常量设计**（MIN_LOAD_FACTOR :61 / **FIXED_LOAD_FACTOR = 1.00f** :66——**固定 1.0 加载因子防扩容**：预知容量场景用满负载避免 resize 浪费）；**isMap 双版本**（Object :85 / Class :106——instanceof vs isAssignableFrom）；**newLinkedHashMap 带 accessOrder 参数**（:655——LRU 支持）；of 工厂多形态（:207-448）
- **对比取舍**：**知识增量：FIXED_LOAD_FACTOR=1.0 的设计**——已知确切容量时用满加载因子消除扩容（时间换空间）；accessOrder 参数化（:655）暴露 LinkedHashMap 的 LRU 能力
- **my-xhs**：**该用没用**——my-xhs 若做缓存（LRU）可参考 accessOrder 参数化；Spring `MapFactory` 有部分覆盖

### 包: `io.microsphere.convert`（批 4：核心机制 52 文件归组）

> 主题：**类型转换 SPI 体系**——README Features 明示 "Extensible Converter SPI"。结构：Converter 接口 + AbstractConverter 模板 + Converters 注册中心 + StringConverter 枢纽 + 38 个具体转换器 + multiple 子包 14 个集合转换器。

#### KP-121 `Converter` SPI 接口（Converter.java:71-166）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（转换 SPI 设计） | **置信度**：High
- **前置**：SPI（confucius KP-32）、泛型类型推断、Prioritized 优先级
- **需求**：**类型转换抽象**——S→T 的转换契约，泛型声明源/目标类型
- **参考实现**：**三个设计决策**：①`@FunctionalInterface`（:71）+ `extends Prioritized`（:72——优先级接口）；②**泛型推断**——`getSourceType()`/`getTargetType()` 用 `resolveActualTypeArgumentClass(getClass(), Converter.class, 0/1)`（:99-110——从泛型实参反射解析源/目标类型，**免注册类型声明**）；③**accept 判定**（:81-83——`isAssignableFrom` 双向检查，SPI 匹配用）；静态工具（getConverter :135 / convertIfPossible :159——**失败返回 null 而非抛异常** :164）
- **对比取舍**：**知识增量**：泛型实参反射解析类型（免配置）——Spring 的 `GenericTypeResolver`/`ResolvableType` 同思路；**convertIfPossible 的"失败返回 null"语义**（:159-165）是设计决策（宽松转换 vs 严格异常）——测试实证（ConverterTest:50——`convertIfPossible("1", Date.class)` 返回 null）；**Prioritized 与 Spring `Ordered` 同构**（Prioritized.java:56 extends Comparable + COMPARATOR 静态比较器 :61-76——处理"一方非 Prioritized"降级 + MAX/MIN/NORMAL 三常量 :81-91）
- **my-xhs**：**该用没用**——my-xhs 若做类型转换层（配置/协议转换）可参考；Spring `ConversionService`/`TypeConverter` 有完整替代

#### KP-122 `AbstractConverter` 模板方法（AbstractConverter.java:88-146）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（模板方法） | **置信度**：High
- **前置**：模板方法模式、异常包装、优先级解析
- **需求**：**转换模板**——空值保护 + 异常统一包装 + 优先级自动解析
- **参考实现**：**四层设计**：①**模板方法**（convert :101-113——null 直接返回 :102-104 + doConvert 抽象 :127 + **Throwable 统一包装 RuntimeException** :108-111 带 warn 日志）；②**优先级自动解析**（resolvePriority :139-146——**类型继承深度编码**：`getAllClasses(sourceType).size() << 16 | getAllClasses(targetType).size()`（:143-145，负号使"更具体类型"优先级更高）——**源类型深度占高 16 位、目标低 16 位**，父类转换器自动排后）；③**null 安全**（@Nonnull/@Nullable 标注使用 :20-21）；④**final 防重写**（convert 标 final :101——模板不可改，子类只实现 doConvert）
- **对比取舍**：**模板方法 + final 防重写**是教科书正确用法（Spring `PropertyEditorSupport`/`ConversionService` 同构）；**继承深度编码优先级**是巧妙的自动排序（免手工指定优先级——子类多、优先级自然低）；异常包装（:108-111）保留原始异常链
- **my-xhs**：**该用没用**——模板方法模式参考（任何"处理链"场景）

#### KP-123 `Converters` SPI 注册中心（Converters.java:42-77）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（SPI 注册） | **置信度**：High
- **前置**：SPI 加载（confucius KP-32 + 本仓库缓存版）、ConcurrentHashMap computeIfAbsent、双键缓存
- **需求**：**转换器注册与查找**——SPI 加载全部 Converter + 按 (sourceType,targetType) 键缓存
- **参考实现**：**三层设计**：①**启动全量加载**（initConvertersCache :48-60——`loadServicesList(Converter.class, classLoader, true)` SPI 加载 :71-73 + **双键缓存** `Entry<Class,Class>` :55 + computeIfAbsent 分组 :56-57）；②**懒查找**（findConverter :62-69——**缓存未命中时按 accept 过滤再缓存** :64-67——懒加载过滤链）；③**取首个**（first(converters) :68——**SPI 顺序 + Prioritized 排序**决定胜负）
- **对比取舍**：**知识增量：双键缓存（Entry 键）+ 懒过滤**——"启动分组 + 懒补充"双层缓存策略；与 Spring `ConversionService` 的 `ConverterRegistry`（注册式）对照——microsphere 是 **SPI 自动发现式**（无需注册，classpath 扫描）；**生态演进实证**：confucius KP-32 的 loadServicesList 无缓存（每次重新加载），本仓库**加了缓存开关**（ServiceLoaderUtils:140 `servicesCache` + :438/:468 重载 + `microsphere.service-loader.cached` 系统属性 :112）——**同主题两代实现的演进**
- **my-xhs**：**该用没用**——SPI 自动发现转换器是自定义协议转换层的参考；Spring ConversionService 是替代

#### KP-124a 数值转换族（NumberToByte/Short/Integer/Long/Float/Double/Character 7 个）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟢 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：Number 类型层级、数值转换语义
- **需求**：**Number → 各数值类型**——数值间的类型转换（宽化/窄化语义）
- **参考实现**：**统一模板**（NumberToIntegerConverter :28——`extends AbstractConverter<Number, Integer>` + doConvert :36——**每个转换器 = 模板 + 单例**；7 个实现同构（extends AbstractConverter<Number, X>））
- **对比取舍**：**知识增量**：数值转换族的**模板复用**（7 文件同构——"一个模板多实现"）；Spring `NumberUtils`/`ConversionService` 覆盖
- **my-xhs**：**不该用**——Spring NumberUtils 覆盖

#### KP-124b 字符串转换族（StringTo* 17 个：Boolean/Byte/Short/Integer/Long/Float/Double/Character/CharArray/Class/Duration/InputStream/String）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：StringConverter 枢纽（StringConverter.java:26）、JDK8 类型（Duration/InputStream）
- **需求**：**String → 各类型**——配置/协议解析的核心（"字符串是传输格式"）
- **参考实现**：**StringConverter 接口族**（StringConverter.java:26——`extends Converter<String,T>`——**String 枢纽标记**）；**JDK8 类型支持**（StringToDurationConverter :29——**Duration 转换**（JDK8 类型））；**INSTANCE 单例模式**（:34——每个转换器单例）；StringToInputStream（字节流转换）
- **对比取舍**：**知识增量**：String 枢纽设计（String 作中间表示——配置/CSV/JSON 通吃）vs Spring ConversionService 直接 S→T 图；**JDK8 类型转换器**（Duration/InputStream——Spring 需自定义）
- **my-xhs**：**该用没用**——配置格式转换（String→任意）；Spring DefaultFormattingConversionService 覆盖

#### KP-124c Object 转换族 + 特殊族（ObjectTo* 10 个 + ByteArrayToObject/ObjectToByteArray/MapToProperties/PropertiesToString）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：Object 泛化转换、Optional/字节数组
- **需求**：**Object → 各类型**（宽泛入口）+ 特殊双向转换（字节数组/Map↔Properties）
- **参考实现**：**Object 入口族**（ObjectToBoolean/Byte/Character/Double/Float/Integer/Long/Short/Optional/String 10 个——**宽泛源类型**（接收任意 Object））；**ObjectToOptional**（:28——`implements Converter<Object, Optional>`——**非 AbstractConverter**（直接实现 + 单例 :33/:36——特殊实现模式））；**互转族**（MapToProperties :28——Map→Properties；PropertiesToString——序列化）；ByteArrayToObject/ObjectToByteArray（字节序列化）
- **对比取舍**：**知识增量**：Object 宽入口 vs String 枢纽（KP-124b）——**两条转换路径**（宽泛 vs 字符串）；ObjectToOptional 的特殊实现（Optional 包装语义）
- **my-xhs**：**不该用**——Spring 覆盖

#### KP-124d 集合转换族（multiple 14 个：MultiValueConverter + StringTo*Collection 13 个）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：MultiValueConverter SPI（multiple/MultiValueConverter.java:36-85）、集合类型层级
- **需求**：**String → 集合类型**——分隔字符串转 List/Set/Queue/Deque/BlockingQueue 等
- **参考实现**：**MultiValueConverter SPI**（:36——`accept(sourceType, multiValueType)` **双类型判定** :45 + SPI 加载 :73-80 + sorted 优先级 :77——**与 KP-123 Converters 同模式**）；**继承树**（StringToCollectionConverter extends StringToIterableConverter<Collection> :27——**模板继承链**：Iterable → Collection → List/Set/Queue/Deque/SortedSet/NavigableSet/BlockingQueue/TransferQueue 13 个变体）；StringToMultiValueConverter（桥接入口）
- **对比取舍**：**知识增量**：集合转换的**继承树**（Iterable 基座 → 13 变体——**模板方法 + 继承**而非 13 个独立类）；双类型 accept（源 + 集合类型双匹配——精确判定）
- **my-xhs**：**该用没用**——分隔字符串转集合（配置解析）；Spring ConversionService 部分覆盖

### 包: `io.microsphere.reflect`（批 5：21 文件/8356 行）

> 主题：**反射工具 + 泛型类型模型**——README Features 明示 "Reflection Utilities"。核心三支柱：JavaType 类型模型 + TypeUtils 解析 + 定义类家族（*Definition）。

#### KP-125 `JavaType` 泛型类型模型（JavaType.java:92-1329）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（类型模型设计） | **置信度**：High
- **前置**：泛型类型系统（Type/ParameterizedType/TypeVariable/WildcardType/GenericArrayType）、JDK 反射 Type 层次
- **需求**：**JDK 反射 Type 的面向对象封装**——把 `java.lang.reflect.Type` 5 种形态（Class/ParameterizedType/TypeVariable/WildcardType/GenericArrayType）统一为可操作的对象模型
- **参考实现**：**Kind 枚举五分类**（:212-216 javadoc 实证——CLASS/PARAMETERIZED_TYPE/TYPE_VARIABLE/WILDCARD_TYPE/GENERIC_ARRAY_TYPE + UNKNOWN :110）；**缓存设计**（interfaces 懒解析缓存 :342-348 volatile + getSuperType :311）；**常用实例预置**（OBJECT_JAVA_TYPE :104 / NULL_JAVA_TYPE :110）；**转换方法**（as(Class) :455 / toClass :488）
- **对比取舍**：**知识增量：Type 的 5 形态模型**——JDK 反射 Type 体系的教学封装（TypeUtils 的谓词过滤器 :78-88 是配套——NON_OBJECT_TYPE_FILTER/TYPE_VARIABLE_FILTER 等 5 个 Predicate）；**与 Spring 对照**：Spring 用 `ResolvableType`（功能更全含 AnnotatedType），microsphere 是轻量自研
- **my-xhs**：**该用没用**——若 my-xhs 做泛型工具（如泛型 DAO 基类）可参考；Spring `ResolvableType` 是替代

#### KP-126 `TypeUtils` 类型解析（TypeUtils.java:78-493 等）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（泛型解析） | **置信度**：High
- **前置**：泛型擦除、Type 层次遍历、缓存
- **需求**：**泛型解析家族**——判断（isParameterizedType 等）、raw 类型提取、**实际类型实参解析**（convert 包 KP-121 依赖的 `resolveActualTypeArgumentClass`）
- **参考实现**：**配置化缓存**（RESOLVED_GENERIC_TYPES_CACHE_SIZE :93-114——系统属性 + 默认 256 三层）；**实际实参解析链**（resolveActualTypeArguments :448 → resolveActualTypeArgument :476 → 类版本 :493）
- **对比取舍**：**resolveActualTypeArgument 是 convert 泛型推断的地基**（KP-121 的 `getSourceType()` 最终调它）——**本包与 convert 包的依赖链实证**；与 Spring `GenericTypeResolver.resolveTypeArgument` 同功能
- **my-xhs**：**该用没用**——Spring `GenericTypeResolver` 覆盖；但**泛型擦除与实参解析机制**是必懂知识

#### KP-127 定义类家族（MemberDefinition/ReflectiveDefinition/ClassDefinition/FieldDefinition/MethodDefinition/ConstructorDefinition/ExecutableDefinition + generics 子包）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式]（元数据模型） | **置信度**：High
- **前置**：反射 Member 层次（Member/Field/Method/Constructor）
- **需求**：**反射成员的对象化元数据**——since/deprecation/声明类等信息的不可变模型（@Immutable :76）
- **参考实现**：继承体系——`ReflectiveDefinition`（基座）→ `MemberDefinition<M extends Member>`（:77——name + 声明类 + since/deprecation :80-101）→ Field/Method/ConstructorDefinition；**泛型限定** `M extends Member` 保证类型安全
- **对比取舍**：**元数据模型 vs 直接反射**——定义类提供声明式访问（Spring `ReflectionUtils` 无此层，直接操作 Member）；自研轻量级
- **my-xhs**：**不该用**——Spring ReflectionUtils + JDK 直接反射覆盖

#### KP-128 `MethodUtils`/`FieldUtils`/`ConstructorUtils` 反射工具（MethodUtils.java:91-365 等 + FieldUtils.java + ConstructorUtils.java + AccessibleObjectUtils.java）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式]（反射工具设计） | **置信度**：High
- **前置**：反射 API、Predicate 组合、模块化 setAccessible
- **需求**：**方法/字段/构造器的查找与过滤**——Predicate 体系 + 禁用名单 + setAccessible 封装
- **参考实现**：**MethodUtils 三层设计**：①**命名常量**（GET/SET/IS 前缀 :91-101——Bean 方法识别）；②**Predicate 工厂**（OBJECT_METHOD_PREDICATE/PUBLIC/STATIC/FINAL 等 :132-157——组合过滤）；③**禁用名单**（banned-methods 系统属性 :113 + banMethod :252——**配置化排除反射干扰方法**）；**AccessibleObjectUtils 的模块化处理**（trySetAccessible :97——**JDK8 及以前传统 setAccessible + JDK9+ 异常提示 --add-opens**（错误消息解析 :200-204 实证——**自动提取包名并提示 add-opens 命令**））
- **对比取舍**：**知识增量：banned-methods 禁用名单**（排除 Object 特殊方法/框架干扰——Spring 用 `findAllMethods` + 过滤器，microsphere 是配置化名单）；**AccessibleObjectUtils 的 --add-opens 错误提示**是开发体验设计（JDK9+ 反射失败的友好诊断）
- **my-xhs**：**该用没用**——Spring `ReflectionUtils` 覆盖主要场景；**--add-opens 错误提示模式**值得借鉴（诊断友好性）

#### KP-129 `ProxyUtils`/`Modifier` 辅助（ProxyUtils.java:41-103 + Modifier.java）

- **维度**：[工程问题] | **权重**：[边缘] | **深度**：🟢 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：Medium
- **前置**：JDK 动态代理、修饰符位运算
- **需求**：动态代理创建 + 修饰符判断工具
- **参考实现**：ProxyUtils（:41——基于 Predicate 过滤方法集后生成代理，与 MethodUtils 联动）；Modifier（修饰符判断封装）
- **my-xhs**：**不该用**——JDK Proxy/Spring AOP 覆盖

### 包: `io.microsphere.util`（批 6：34 文件/17275 行）

> 主题：**生态标准工具库**——confucius-commons 的完整继任（类名大量同名，confucius KP-01~35 同主题扩展版）。核心：ClassLoaderUtils（2138 行）/ClassUtils（2327）/ArrayUtils（2186）/AnnotationUtils（1839）/StringUtils（963）大文件 + Assert/StopWatch/Version/Compatible 等特色工具。

#### KP-130 `ClassLoaderUtils` 完整版（ClassLoaderUtils.java:107-993 等）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（类加载工具） | **置信度**：High
- **前置**：confucius KP-01~05（同名类——**交叉引用**）、ClassLoader 层级、缓存
- **需求**：类加载工具完整版——confucius 版（KP-01 反射 findLoadedClass）+ **null 安全 + 缓存 + 调用者感知** 扩展
- **参考实现**：**五个扩展点**（对比 confucius）：①**null 安全**（nullSafeClassLoader :218/getDefaultClassLoader :244——**TCCL 优先降级**）；②**调用者加载器**（getCallerClassLoader :327——**栈帧探测**：实证实现 = `StackTraceUtils.getCallerClassInStatckTrace(5)`（:1833-1836）——**非 StackWalker**！是 `Thread.getStackTrace()` 遍历 + 帧偏移常量（StackTraceUtils :61-64——注释自证 "+3 帧偏移"）；③**类加载缓存**（loadClass(classLoader, className, cached) :589——**三参带缓存版本**——confucius 无）；④**资源字符串化**（getResourceAsString :876/:915——**资源读为字符串的便捷封装**）；⑤**ResourceType 复用**（getResources :669-707 同 confucius KP-04 设计）
- **对比取舍**：**生态演进实证（同主题两代）**——confucius KP-01~05 vs 本包：null 安全（@Nullable 参数 :357 等）+ 缓存（:589）+ 调用者感知（:327）三扩展；**getCallerClassLoader 的栈帧探测**与 confucius KP-28（sun.reflect.Reflection）对照——本仓库已弃用 sun.reflect（JDK17 移除），改 `getStackTrace()` 遍历（StackTraceUtils）——**但仍有帧偏移魔法数**（+3 :64，同 confucius KP-28 的缺陷类型）；**拼写细节**：方法名 `getCallerClassInStatckTrace`（StacKTrace 拼错——全库统一错误命名，API 稳定性约束无法改名）
- **my-xhs**：**不该用**——Spring `ClassUtils`/`ResourceUtils` 覆盖；类加载机制知识本体保留（outline 已提炼）

#### KP-131 `Assert` 断言工具（Assert.java:45-509）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[过时→Spring `Assert`] | **置信度**：High
- **前置**：断言模式、Supplier 延迟求值
- **需求**：**契约校验**——assertTrue/assertNull/assertNotEmpty/assertNotBlank + **Supplier<String> 消息延迟求值**（:79/:114 等——**错误消息只在失败时构造**）
- **参考实现**：**双签名模式**（String message / Supplier<String> messageSupplier 每方法成对 :60-77/:79-94 等——**性能设计**：消息构造成本仅在断言失败时支付）；与 Spring `Assert` 完全同构
- **对比取舍**：**Supplier 消息延迟求值是性能细节**（Spring 同款）；无知识增量但**是我方代码风格基准**（后续 KP 大量使用 assertNotNull + lambda）
- **my-xhs**：**该用没用**——Spring `Assert` 已用（my-xhs 现状）；本包版是自研替代

#### KP-132 `Version` 语义化版本模型（Version.java:91-791）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式]（版本比较） | **置信度**：High
- **前置**：语义化版本（SemVer）、Comparable
- **需求**：**版本比较与解析**——major/minor/patch/preRelease 的 Comparable 实现
- **参考实现**：`implements Comparable<Version>`（:91）+ compareTo（:372）+ 静态工厂（of :441-493 重载链）；**被定义类家族引用**（reflect KP-127 的 MemberDefinition since 用 `ofVersion`——跨包引用实证）
- **对比取舍**：SemVer 比较的时间无关模式；Spring 无内建（用 `Version` 类在 spring-boot）
- **my-xhs**：**该用没用**——my-xhs 若做版本兼容判断（如接口版本）可用；或 Spring Boot `Version`/Maven `ComparableVersion`

#### KP-133 `Compatible` 版本条件执行（Compatible.java:83-272 + Version.java:671+）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式]（版本条件） | **置信度**：High
- **前置**：版本比较（KP-132）、条件执行、Optional、BiPredicate
- **需求**：**按版本条件执行代码**——`Compatible.of(Class).on(">=", "2.0").call()` 模式：版本满足才执行回调
- **参考实现**：**构建器链**（of :140 → on(operator, version) :166/:193 → call :220 返回 Optional<R> / get :272 / accept :247）；**Operator 枚举实现在 Version.java:671**（`implements BiPredicate<Version,Version>`——**EQ/LT/LE/GT/GE/NQ 每态 test 实现** + null 处理：同引用 true（:677-679）/null 互斥 false（:681-682）——**null 安全比较语义**）；与 `@Since` 注解（KP-104）联动思路
- **对比取舍**：**知识增量：版本条件执行是独特设计**——按运行环境版本动态分支（Spring 无直接等价；Java 用 `Runtime.version().feature()` + 手写 if）；**Operator 的 BiPredicate 实现**（比较算子枚举化——状态模式）；**"声明式版本分支" DSL 模式**；测试实证（CompatibleTest:34-41——链式 4 条件 on("<")/on("=")/on(">")/on(">=")，最终 get()==">=" :41）
- **my-xhs**：**该用没用**——my-xhs 若做多版本兼容（如第三方 SDK 适配）可参考；**DSL 链设计**值得学

#### KP-134 `ShutdownHookUtils` 关闭钩子管理（ShutdownHookUtils.java:86-142 等 + ShutdownHookCallbacksThread）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式]（关闭钩子） | **置信度**：High
- **前置**：Runtime.addShutdownHook、回调注册
- **需求**：**关闭钩子的批量管理**——多个回调注册到一个专用线程
- **参考实现**：**专用线程模式**（ShutdownHookCallbacksThread——所有回调在专用线程执行 + 过滤器 SHUTDOWN_HOOK_CALLBACKS_THREAD_FILTER :112 识别）+ **容量配置化**（shutdown-hook.callbacks-capacity 系统属性 + 默认 512 :86-107 三层）+ registerShutdownHook 懒注册（:137-142）
- **对比取舍**：**专用线程 + 容量限制**是关闭钩子的工程化设计（防止单回调阻塞/无限注册）；Spring `ApplicationListener` 无此专门管理
- **my-xhs**：**该用没用**——my-xhs 若有多个清理任务（连接池/线程池优雅关闭）可参考；Spring `@PreDestroy` 覆盖多数场景

#### KP-135 `TypeFinder`/`StopWatch`/`Functional`/`Configurer` 特色工具

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：Medium
- **前置**：类型层次遍历、计时、函数式包装
- **需求**：类型相关查找（TypeFinder）、代码计时（StopWatch）、函数式值包装（Functional）、配置器（Configurer）
- **参考实现**：TypeFinder（:86——Include 枚举 HIERARCHICAL/INTERFACES :64-72 + classFinder 工厂——**类型层次遍历**）；StopWatch（:44-48——**任务名 + 可重入控制** :36-38 非重入默认——比 Spring StopWatch 多可重入选项）；Functional（:62——**命名值包装** of(name, supplier) :239）；Configurer（:445——配置器模式）
- **对比取舍**：**StopWatch 可重入控制**是超出 Spring 的设计（防止嵌套计时误用）；TypeFinder 与 Spring `ClassUtils.getUserClass`/`GenericTypeResolver` 部分重叠
- **my-xhs**：**该用没用**——StopWatch 计时（my-xhs 压测场景 KP-132 同组）；Spring `StopWatch` 是替代

### 包: `io.microsphere.event`（批 7a：10 文件）

> 主题：**轻量事件分发器**——README Features 明示 "Event Dispatching with sequential and parallel execution modes"。核心：EventDispatcher 接口 + AbstractEventDispatcher 模板 + Direct/Parallel 两实现。

#### KP-136 `EventDispatcher` 事件分发 SPI（EventDispatcher.java:55-98）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（事件分发设计） | **置信度**：High
- **前置**：观察者模式、Executor、事件类型体系
- **需求**：**事件分发抽象**——监听器注册/分发，支持直接/并行执行策略
- **参考实现**：**策略注入设计**：`DIRECT_EXECUTOR = Runnable::run`（:60——**直接执行 = 同步调用**）；工厂（newDefault :67 → DirectEventDispatcher / parallel(executor) :77 → ParallelEventDispatcher）；`getExecutor()` 默认 DIRECT（:95-97）；extends `Listenable<EventListener<?>>`（:55——监听器管理接口）
- **对比取舍**：**Executor 策略模式**（执行策略注入 vs 硬编码）——Spring `ApplicationEventMulticaster` 同思路但更重（多播器注册式）；microsphere 是轻量 SPI 式
- **my-xhs**：**该用没用**——若 my-xhs 做轻量内部事件（非 Spring 环境）可用；Spring `ApplicationEventPublisher` 覆盖主流

#### KP-137 `AbstractEventDispatcher` 分发模板（AbstractEventDispatcher.java:92-215）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（分发机制） | **置信度**：High
- **前置**：模板方法、事件类型匹配（isAssignableFrom）、条件监听器、SPI
- **需求**：**分发器通用实现**——按事件类型匹配监听器 + 优先级排序 + 条件过滤 + SPI 自动加载
- **参考实现**：**六大机制**：①**类型键缓存**（listenersCache `ConcurrentMap<Class<? extends Event>, List<EventListener>>` :98——按事件类型分组缓存）；②**类型匹配分发**（dispatch :161-177——`entry.getKey().isAssignableFrom(event.getClass())` :165——**父类型监听器接子类型事件**——继承感知匹配）；③**条件监听器**（ConditionalEventListener :170-173——accept 过滤；接口定义 :47-56 `boolean accept(E event)`）；④**优先级排序**（sortedListeners :140-151——Prioritized 排序 + **缓存变更后即时重排**（doInListener :205-207 `sort(listeners)` 同步更新——**写入时排序而非读取时**））；⑤**Executor 执行**（:163——direct/parallel 策略）；⑥**SPI 自动加载**（loadEventListenerInstances :211+——`loadServicesList(EventListener.class...)` SPI 加载）
- **对比取舍**：**知识增量**：①**类型键缓存 + isAssignableFrom 继承匹配**——Spring 的监听器匹配（ResolvableType 精确匹配 + 继承链）vs microsphere 的 isAssignableFrom 简化（**父子类型都命中**——更宽松）；②**写入时排序**（doInListener synchronized + sort :199-207——**注册/移除时持锁重排**，读取时免锁流式遍历——并发设计）；③**ConditionalEventListener 条件过滤**是 Spring `ApplicationListener` + 手动判断的封装；测试实证（EventDispatcherTest:45-46——默认实例 DIRECT + 监听器数）
- **my-xhs**：**该用没用**——轻量事件模型参考；Spring Event 覆盖主流

#### KP-138 事件模型（Event/GenericEvent/GenericEventListener/EventListener/ConditionalEventListener/Listenable）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：泛型事件、观察者模式
- **需求**：事件/监听器类型体系——泛型化 + 条件化
- **参考实现**：Event（基类）+ **GenericEvent<T>**（泛型事件——携带任意负载）+ EventListener<E extends Event>（**泛型监听器**）+ ConditionalEventListener（**accept 前置过滤**）+ Listenable（注册/移除管理）
- **对比取舍**：**泛型事件 + 条件监听器**设计（Spring 的 ApplicationEvent 无泛型负载、无内置条件）；**泛型化是 microsphere 的轻量增强**
- **my-xhs**：**该用没用**——泛型事件设计参考；Spring 覆盖主流

### 包: `io.microsphere.logging`（批 7b：8 文件）

#### KP-139 `LoggerFactory` SPI 日志门面（LoggerFactory.java:65-100 + Logger.java:62-214 + 4 实现）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[过时→SLF4J（已内置实现）] | **置信度**：High
- **前置**：SLF4J 门面模式、SPI、日志级别
- **需求**：**日志门面抽象**——不绑定具体实现（JDK Logging/SLF4J/NoOp），SPI 自动选择
- **参考实现**：**LoggerFactory 是 SPI 工厂**（:65 implements Prioritized）——**选择链实证**（loadFactory :73-75 `get(0)` → loadAvailableFactories :78-83（SPI 加载 + **isAvailable 过滤** `removeIf(negate)` :81）→ loadFactories :85-89（SPI 加载 + **Prioritized COMPARATOR 排序** :87——**优先级决定门面选择**：SLF4J 工厂优先级最高则胜出）；**Logger 接口**（:62——trace/debug/info/warn/error 五级 + 格式化重载 :98/:131 等 + Throwable 重载 :107/:140）；**4 实现**（JDKLoggerFactory/Sfl4jLoggerFactory/ACLLoggerFactory/NoOpLoggerFactory）
- **对比取舍**：**SLF4J 门面模式的复刻**——**但 SLF4J 已存在且更成熟**（microsphere 重造轮子）；**SPI 工厂 + isAvailable 过滤 + Prioritized 排序**是门面选择的完整模式（三要素：可用性+优先级+SPI）
- **my-xhs**：**不该用**——SLF4J + Logback（my-xhs 现状）覆盖

### 包: `io.microsphere.io`（批 7c：27 文件）

#### KP-140 `IOUtils` 流工具（IOUtils.java:57-268 等）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[过时→Spring `StreamUtils`/commons-io] | **置信度**：High
- **前置**：InputStream 读取、字符集、缓冲区
- **需求**：流读取家族——readLines/toString/toByteArray + **缓冲区配置化**
- **参考实现**：**缓冲区三层配置**（DEFAULT_BUFFER_SIZE :66 / BUFFER_SIZE_PROPERTY_NAME :80 / BUFFER_SIZE :91——`microsphere.io.buffer.size` 系统属性——与 KP-108/KP-134 同款配置化模式）
- **my-xhs**：**不该用**——Spring StreamUtils 覆盖

#### KP-141 `Serializer` SPI 序列化体系（Serializer.java:40-42 + Serializers.java:36-46 + 实现家族）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式]（序列化 SPI） | **置信度**：High
- **前置**：序列化、SPI、优先级
- **需求**：**对象↔字节序列化 SPI**——按类型选最兼容序列化器
- **参考实现**：Serializer<S>（:40——**泛型源类型** `byte[] serialize(S)` :42）；**Serializers 注册中心**（:36-46——**SPI 加载 + getHighestPriority 选择**——与 convert 包 Converters（KP-123）**同构模式**：SPI 加载 + 优先级选优）；实现家族（Default/String/DefaultDeserializer/StringDeserializer）
- **对比取舍**：**与 KP-123 Converters 的模式复现**——"SPI 加载 + Prioritized 选优"是 microsphere 生态的**统一注册中心模式**（convert/io 两处实证）
- **my-xhs**：**该用没用**——SPI 序列化模式参考；Jackson/Kryo 覆盖主流

### 包: `io.microsphere.classloading`（批 8a：16 文件）

#### KP-142 `ArtifactDetector` 类路径构件探测（ArtifactDetector.java:76-137 + Artifact.java:35-49）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式]（classpath 探测） | **置信度**：High
- **前置**：classpath 解析（confucius KP-06~10）、URL 协议、Maven 构件
- **需求**：**探测 classpath 中的构件**（Artifact：artifactId/version/location）——运行时识别"哪个 jar 在 classpath"（版本冲突诊断/依赖分析）
- **参考实现**：**三层设计**：①**Artifact 模型**（:35-49——artifactId/version/location + @Immutable）；②**探测 API**（detect() 无参 :102 / 含 JDK 库 :108 / 指定 URL 集 :115 / 按类 :131——**从类定位所属构件**）；③**Resolver SPI**（ArtifactResourceResolver——AbstractArtifactResourceResolver + ArchiveFile/Manifest/Maven/Stream 实现——**按 URL 类型分派**：Maven artifact 从 pom.properties 解析版本）
- **对比取舍**：**知识增量**：①**"从类反查构件"**（:131——类 → classpath URL → Artifact）是依赖诊断的关键能力（Spring Boot `ArtifactUtils` 有类似）；②**Resolver SPI 按 URL 协议分派**（与 KP-141 Serializers 同模式）；③**BannedArtifactClassLoadingExecutor**（:63-84——**`META-INF/banned-artifacts` 配置禁用构件类加载**——与 util 包 banned-methods（KP-128）**同家族模式**：配置化黑名单）
- **my-xhs**：**该用没用**——依赖冲突诊断场景；Maven `dependency:tree` 是替代（构建期）

### 包: `io.microsphere.net`（批 8b：13 文件）

#### KP-143 `URLStreamHandler` 自定义协议体系（CompositeURLStreamHandlerFactory.java:56-132 等）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式]（URL 协议扩展） | **置信度**：High
- **前置**：URLStreamHandlerFactory、自定义协议、JDK URL 机制
- **需求**：**自定义 URL 协议**——microsphere 生态扩展 `URL.setURLStreamHandlerFactory` 的能力（SPI 加载 + 组合工厂）
- **参考实现**：**六件套**：CompositeURLStreamHandlerFactory（:56——**工厂组合 + 顺序委派**（:75-80 逐个试））；DelegatingURLStreamHandlerFactory（委托）；ServiceLoaderURLStreamHandlerFactory（**SPI 加载**）；MutableURLStreamHandlerFactory（可变）；StandardURLStreamHandlerFactory（标准）；ExtendableProtocolURLStreamHandler（可扩展处理器）；配套 SubProtocolURLConnectionFactory/DelegatingURLConnection（**子协议连接工厂**）
- **对比取舍**：**知识增量：URL 协议扩展是 JDK 的"最后手段"**（URL.setURLStreamHandlerFactory 全局只能设一次——组合工厂解决**单例限制**）；Spring 用 `Resource` 抽象（协议无关）替代；**扩展 JDK URL 协议 vs 用 Resource 抽象**是架构取舍
- **my-xhs**：**不该用**——Spring Resource 覆盖；URL 工厂单例限制是坑

### 包: `io.microsphere.metadata`（批 8c：9 文件）

#### KP-144 `ConfigurationPropertyLoader` 元数据加载链（ConfigurationPropertyLoader.java:47-97 + 实现家族）

- **维度**：[工程问题] | **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（元数据 SPI） | **置信度**：High
- **前置**：KP-101（@ConfigurationProperty 注解）、KP-109（运行时对象）、annotation-processor 产出、SPI
- **需求**：**配置属性元数据加载**——从编译期元数据（JSON 文件）或反射加载 ConfigurationProperty 列表——**KP-101 注解体系的运行时闭环**
- **参考实现**：**SPI 加载链**（loadAll :91-97——`loadServicesList(ConfigurationPropertyLoader.class)` + 逐个 load）；**实现家族分派**：ClassPathResourceConfigurationPropertyLoader（classpath 元数据）/MetadataResourceConfigurationPropertyLoader（`META-INF/microsphere/configuration-properties.json`——**KP-105 ResourceConstants 定义的文件**）/AdditionalMetadataResource（附加文件）/ReflectiveConfigurationPropertyGenerator（**反射回退**——注解直接反射生成）
- **对比取舍**：**知识增量：注解体系的完整闭环**——编译期（annotation-processor 生成 JSON）→ 运行期（本加载链读取）→ 对象（KP-109）——**三阶段联动**（对比 Spring：`@ConfigurationProperties` 无编译期生成，运行期 Binder 反射）；**SPI 加载器链**（多 loader 叠加）是元数据聚合模式
- **my-xhs**：**该用没用**——注解驱动配置框架的参考；Spring `@ConfigurationProperties` 覆盖主流

### 包: `io.microsphere.json` / `concurrent` / `invoke` / `misc` / `nio` / `security` / `text` / `filter`（批 8d：小包归组）

#### KP-145a JSON 包（Android 移植 8 文件：JSONObject/JSONArray/JSONTokener/JSONStringer/JSONException/JSON/JSONUtils）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P3 | **过时**：[过时→Jackson（Android JSON 库移植）] | **置信度**：High
- **前置**：JSON 语法、递归下降解析、Android 平台 JSON
- **需求**：**JSON 解析/生成**——JDK6 时代无内建 JSON
- **参考实现**：**来源实证：Android OpenJDK 移植**（JSONObject.java 头部版权 "Copyright (C) 2010 The Android Open Source Project" 实证——**与 KP-35 Base64（Josh Bloch）、KP-103（JCIP）同移植模式**：第三方/平台代码复制保版权头——**第三例移植来源**）；**JSONUtils 便捷层**（:113——append 系列 :146-176——**JSON 构建工具**）
- **对比取舍**：**知识增量**：移植来源识别（版权头实证——识别"这是谁的代码"的方法论）；**重造轮子判断**（Android 老版 JSON 实现 vs Jackson 标准——移植旧代码不升级是技术债）
- **my-xhs**：**不该用**——Jackson 覆盖

#### KP-145b 并发工具（concurrent 4 + invoke 2 = 6 文件）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：ThreadFactory、Executor 委托、MethodHandle
- **需求**：**线程工厂定制**（命名/守护/优先级/栈大小）+ Executor 委托 + MethodHandle 工具
- **参考实现**：**CustomizedThreadFactory**（:40——implements ThreadFactory + **定制化属性**（javadoc :30-34——命名/daemon/优先级/栈大小 1024）——**可控线程工厂**）；**Delegating 家族**（DelegatingBlockingQueue/DelegatingScheduledExecutorService——委托包装）；**invoke 2 文件**（MethodHandlesLookupUtils——**KP-117 MethodHandle 探测的实现地**（findPublicStatic 等——**版本探测模式的实际实现**）/MethodHandleUtils）
- **对比取舍**：**知识增量**：定制化线程工厂（命名+属性——生产线程命名规范）；MethodHandle 探测的实现细节（KP-117 依赖链实证）
- **my-xhs**：**该用没用**——线程命名规范（my-xhs 线程池诊断参考）；Spring 无直接等价

#### KP-145c 小包归组（misc 1 + nio 1 + security 1 + text 1 + filter 9 = 13 文件）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟢 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：Medium（未深读）
- **前置**：filter 家族（confucius KP-13~18 同主题）、编码/安全工具
- **需求**：过滤（filter 9——confucius 同主题扩展）+ 杂项（misc/nio/security/text）
- **参考实现**：filter（9 文件——Filter/FilterOperator/FilterUtils 等——**confucius KP-13~18 同主题扩展**（交叉引用））；misc/nio/security/text 各 1（编码/安全/文本工具）
- **my-xhs**：**不该用**——JDK/Spring 覆盖

### 模块: `microsphere-lang-model`（16）+ `microsphere-annotation-processor`（4）+ `microsphere-jdk-tools`（1）

#### KP-146a `ConfigurationPropertyAnnotationProcessor` 编译期注解处理器（annotation-processor 4 文件）

- **维度**：[规范] | **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式]（APT 编译期处理） | **置信度**：High
- **前置**：javax.annotation.processing（AbstractProcessor/RoundEnvironment）、javax.lang.model、@ConfigurationProperty（KP-101）、Filer
- **需求**：**编译期生成配置元数据 JSON**——@ConfigurationProperty 注解 → configuration-properties.json（KP-105 文件约定）
- **参考实现**：**@SupportedAnnotationTypes**（:77——声明处理 @ConfigurationProperty）；**AbstractProcessor 生命周期**（init :89-91——ProcessingEnvironment/Messager 获取）；**处理链**（process 每轮 :51-57 javadoc → ConfigurationPropertyJSONElementVisitor（**元素访问器**——遍历注解元素收集元数据）→ writeMetadata（:64——**Filer 写入 JSON 文件**））；**FilerProcessor/ResourceProcessor**（Filer 资源写入工具）
- **对比取舍**：**知识增量：编译期元数据生成的完整流程**（APT 生命周期：SupportedAnnotationTypes → init → process → Filer 输出）；**编译期 vs 运行期**（类型安全 + 启动快 vs 反射）
- **my-xhs**：**该用没用**——自定义注解处理器参考（若做编译期校验/生成）

#### KP-146b lang-model 语言模型工具（lang-model 16 文件）

- **维度**：[规范] | **权重**：[支撑] | **深度**：🟡 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：Medium（部分深读）
- **前置**：javax.lang.model（Element/TypeMirror/AnnotationValue）、注解处理
- **需求**：**语言模型 API 封装**——Element/Type/AnnotationValue 的工具化
- **参考实现**：**工具家族**（ElementUtils/TypeUtils/MethodUtils/FieldUtils/ConstructorUtils/MemberUtils/AnnotationUtils/LoggerUtils/MessagerUtils——**javax.lang.model 各对象的工具化**）；**JSON 访问器**（JSONElementVisitor/JSONAnnotationValueVisitor/AnnotatedElementJSONElementVisitor——**Element → JSON 序列化**（配合 KP-146a 元数据生成））；**ResolvableAnnotationValueVisitor/StringAnnotationValue**（注解值访问器）；ExecutableElementComparator
- **对比取舍**：**知识增量**：javax.lang.model 的**完整工具化**（Spring 无此层）；Element → JSON 访问器（编译期元数据化的基础设施）
- **my-xhs**：**该用没用**——语言模型工具参考（注解处理场景）

#### KP-146c jdk-tools（1 文件）

- **维度**：[规范] | **权重**：[边缘] | **深度**：🟢 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：Low（未读）
- **前置**：JavaCompiler API
- **需求**：JDK 工具桥接（compiler/annotation processing API）
- **my-xhs**：**不该用**

#### KP-120 `QueueUtils`/`SetUtils`/`ListUtils` 专用工具（QueueUtils.java:52-146 + SetUtils.java:74-890 + ListUtils.java:73-738）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟢 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：队列/集合/列表 API
- **需求**：各集合类型的专用判空/转换/首尾访问
- **参考实现**：QueueUtils（EMPTY_DEQUE 常量 :52 + isQueue :70/isDeque :108 + emptyQueue/emptyDeque :127/:146）；SetUtils（ofSet 多形态 :125-234）；ListUtils（isList :73/:90 + first/last :111/:132——**Deque 场景用 peekFirst 语义**）
- **my-xhs**：**不该用**——JDK/Spring 覆盖

---

## 三、深度 review 补充提取（2026-08-13 轮——穷尽性核对发现 160/342 未提及）

> **核对方法**：basename 级 grep 全量核对（方法论铁律 #1 严格执行）——发现本仓库交接声称"穷尽性 323/323"与事实不符（342 文件 160 未提及）。
> **甄别结论**：160 个未提及文件中——**知识已覆盖仅文件名未列**（Converter 家族 40+ 个 = 转换框架 KP 归组；URLStreamHandler/Artifact 等——归组未列全文件名——铁律 #1"覆盖表声称 N/N 但正文缺"再犯）；**知识真遗漏 7 族**（本补充提取）。

### KP-120a `io.microsphere.event` 事件系统（10 文件全族补充）

- **维度**：[工程问题]（事件系统）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：监听器模式、Executor 策略、泛型事件
- **需求**：**自建事件分发系统**——EventDispatcher 接口 + 直接/并行两种分发策略 + 条件监听器（历史 02-06-event-system 主题——microsphere 事件体系源头）
- **参考实现**：**分发契约**（EventDispatcher extends Listenable\<EventListener<?>> :55 + dispatch(Event) :86 + **getExecutor 默认直调**（:95——`default Executor——直接 invoke Runnable.run() 而非多线程`——**顺序语义默认**）；**并行分发器**（ParallelEventDispatcher :33——extends AbstractEventDispatcher + **ForkJoinPool.commonPool()**（:27/:30——JDK 公共池并行分发——**03 仓库 parallel 主题的源头**（历史 03-02 并行 Bean 初始化引用））；**直接分发器**（DirectEventDispatcher——顺序执行）；**抽象基座**（AbstractEventDispatcher——监听器注册/移除 + **按事件类型分派缓存**（:50——thread-safe 类型→监听器映射）+ 执行策略注入）；**事件载体**（Event/GenericEvent——泛型事件）；**条件监听器**（ConditionalEventListener——按条件接收）；**工厂**（EventDispatcher.newDefault()/parallel(executor) :31-40）
- **对比取舍**：**知识增量**：①**事件分发策略化**（顺序/并行两实现 + Executor 注入——vs Spring ApplicationEventMulticaster 的 TaskExecutor 配置——**JDK 级轻量事件系统**（不依赖 Spring——02 仓库"生态中立"实证）；②**ForkJoinPool.commonPool 并行**（:27——公共池共享——注意：**阻塞任务会占公共池线程**（性能隐患））；③**按事件类型分派缓存**（:50——注册时按类型索引——O(1) 查找）
- **my-xhs**：**不该用（实证 2026-08-13）**——my-xhs 无 EventDispatcher 族（MCP 搜索 0 结果）——Spring ApplicationEvent 覆盖；**事件分发策略模式**（顺序/并行/Executor 注入）可借鉴（若 my-xhs 需要并行事件）
- **测试佐证**：event 包测试族（AbstractEventDispatcherTest/ConditionalEventListenerTest 等——分发/条件监听断言）

### KP-120b `io.microsphere.io` IO 族（Serializer/Deserializer 序列化 SPI + FileWatchService 文件监听 + FastByteArray 字节流）

- **维度**：[工程问题]（IO/序列化）| **权重**：[核心] | **深度**：🔴 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：SPI 加载（Serializers 用 SPI 加载 + Prioritized 选优——呼应 KP-1xx SPI 注册中心）、WatchService（JDK 文件监听）
- **需求**：**序列化 SPI + 文件监听服务**——线程安全序列化器族（Serializer\<S> :40——serialize(S) :42）+ SPI 加载/优先级选优（Serializers :36——"load serializers via SPI + 最兼容 + 按优先级"——**SPI 注册中心模式的 IO 域应用**）+ 文件变更监听（FileWatchService :54——注册监听器到文件/目录）
- **参考实现**：**序列化契约**（Serializer\<S> :40-42——泛型 + 线程安全约束（:24——"must be thread-safe"）+ Serializers SPI 加载（:36）+ StringSerializer/DefaultSerializer 等实现）；**反序列化族**（Deserializer/Deserializers/StringDeserializer/DefaultDeserializer）；**字节流**（FastByteArrayInputStream/OutputStream——**快速字节数组流**（无同步开销的自研流——对比 ByteArrayOutputStream synchronized 方法））；**文件监听**（FileWatchService :54——文件/目录变更监听接口 + StandardFileWatchService（JDK WatchService 实现））
- **对比取舍**：**知识增量**：①**序列化 SPI + 优先级选优**（SPI 模式在 IO 域复用——生态中立序列化抽象）；②**Fast 字节流**（无 synchronized 字节流——**性能微优化**（vs ByteArrayOutputStream 全同步）——线程不安全换取性能的取舍）；③**文件监听抽象**（封装 JDK WatchService——目录/文件级变更通知）
- **my-xhs**：**已用（序列化被 Jackson 替代，实证）**——my-xhs JacksonConfig/RedisConfig.createJsonSerializer（:126-149）——**Jackson 生态覆盖序列化**（微球 SPI 序列化无场景）；**FileWatchService 无对应**（my-xhs 无文件监听需求）；Fast 字节流模式可借鉴（若高并发 IO）

### KP-120c `classloading` + `filter` + `concurrent` 族（URLClassPathHandle 策略族 10 + ClassFilter/JarEntryFilter 6 + ExecutorUtils）

- **维度**：[工程问题]（类加载/过滤/并发工具）| **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：URLClassPath（JDK 内部）、Prioritized 排序、Predicate 过滤
- **需求**：**URLClassPath 操作策略 + 过滤接口 + 线程池工具**——classpath 索引的 URLClassPath 侧处理（历史 02-05-classpath-indexing 补全）
- **参考实现**：**URLClassPath 策略族**（URLClassPathHandle :71——implements **Prioritized**（:71——**优先级排序的策略**——行号深度 review 修正（原 :30 为 javadoc 引用区））+ supports()（:78——支持性判定——Classic/Modern/NoOp/ServiceLoading/AbstractURLClassPathHandle 五实现——**JDK 版本分派的 classpath 处理**（Classic=旧 JDK/Modern=新 JDK/ServiceLoading=SPI 加载——**版本策略模式**））——**测试实证**：URLClassPathHandle 族 7 测试（Classic/Modern/NoOp/ServiceLoading/Base/Abstract/URLClassPathHandleTest——每实现独立测试）；**过滤接口族**（ClassFilter extends Filter\<Class<?>> :29 + JarEntryFilter extends Filter\<JarEntry> :29——泛型过滤 + 实现（PackageNameClassFilter/ClassNameFilter/TrueClassFilter 等））；**线程池工具**（ExecutorUtils :40——shutdown/shutdownOnExit（:63——**退出钩子关闭线程池**——资源清理工具族）
- **对比取舍**：**知识增量**：①**版本分派策略**（URLClassPathHandle 族——同一能力按 JDK 版本选择实现——**Classic/Modern 分派模式**（呼应 02 MethodHandle 版本探测的另一个变体——实现类分派 vs API 探测））；②**Filter 泛型族**（Predicate 语义的领域化封装）
- **my-xhs**：**不该用（实证）**——JDK URLClassPath 内部 API 无场景（--add-opens 时代）；Filter 泛型族被 Predicate 覆盖；**ExecutorUtils.shutdownOnExit 该用没用**（my-xhs 直接 newFixedThreadPool（BatchInsertExecutor:73-137 实证）——无统一关闭工具——shutdownOnExit 模式可借鉴）
