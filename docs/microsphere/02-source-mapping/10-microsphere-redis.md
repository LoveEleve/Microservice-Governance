# microsphere-redis 知识点提取

> 源码：`/data/workspace/java-training-camp/cloud-native-code/stage-4/microsphere-redis`（依赖链第 10 站，gateway 之后；5 模块 86 生产文件 + 95 测试）
> 提取时间：2026-08-13（批 1：serializer 20；批 2：interceptor/event/beans/annotation；批 3：metadata/replicator/generator/dynamic/condition）
> 状态：批 1-3 已提取（骨架归组式）；MCP 索引已建（3184 节点/8498 边）
> 关联：**my-xhs zone/redis/ 族 7 文件（对应物实证——EventPublishingRedisCommandInterceptor/RedisCommandEvent 同名）**；02 仓库序列化 SPI；08 仓库自研客户端对照
> 历史交叉验证：`microsphere-analysis/08-microsphere-redis-analysis/`（1 篇 + 08-REQ——Redisson 视角规划文档）

## 一、仓库定位

**Redis 基础设施增强**——命令级拦截/事件化、跨实例逻辑复制（Kafka）、连接工厂 AOP 代理、Template 包装、命令接口元数据 + Doclet 编译期生成、序列化器家族。模块：core/spring（主战场）/spring-boot/generator（doclet）/replicator-spring（Kafka 复制）。核心维度：[分布式问题]（Redis 可观测/复制）+ [工程问题]（SDR 扩展）。

**历史 REQ 定位**（Redisson 视角——3 生产缺口）：命令级拦截 SPI/跨实例逻辑复制/操作级监控——microsphere 现有实现基于 **SDR（Spring Data Redis）RedisConnection 代理**（非 Redisson）。

## 前置条件清单

读者需先掌握：1. Spring Data Redis（RedisTemplate/RedisConnection/命令接口 RedisCommands）2. 02 仓库序列化 SPI/SPI 注册中心 3. Kafka（replicator 通道）4. Javadoc Doclet API（generator）
未达前置者，先补：02 仓库 outline + spring-data-redis 生态

## 掌握度

目标读者：中级偏上（读源码多、Spring 熟——HANDOVER 画像）
讲解策略：与 my-xhs redis 族对照（同名移植实证）+ SDR 扩展点教学

---

## 二、逐文件映射 + 原子记录（骨架归组式）

### 包: `io.microsphere.redis.spring.serializer`（批 1：20 文件）

#### KP-901 序列化器家族（AbstractSerializer 模板 + 定长校验 + 专业参数序列化）（serializer 20 文件全列：AbstractSerializer/BooleanSerializer/BoundarySerializer/ByteArraySerializer/DoubleSerializer/EnumSerializer/ExpirationSerializer/GeoLocationSerializer/HoldingValueRedisSerializerWrapper/IntegerSerializer/LongSerializer/PointSerializer/RangeModel/RangeSerializer/RedisCommandEventSerializer/RedisZSetCommandsRangeSerializer/Serializers/ShortSerializer/SortParametersSerializer/WeightsSerializer）

- **维度**：[工程问题]（序列化）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：RedisSerializer（SDR 接口）、模板方法、定长编码
- **需求**：**Redis 值/命令参数的二进制序列化族**——基础类型（int/long/short/double/boolean/enum）+ **Redis 专业类型**（GeoLocation/Point/Boundary/Range/SortParameters/Weights——ZSet 范围/排序参数/权重）+ 命令事件
- **参考实现**：**模板基座**（AbstractSerializer\<T> :52——implements RedisSerializer\<T>——serialize/deserialize **final**（:87/:97）+ doSerialize/doDeserialize 抽象（:171）+ **calcBytesLength 定长校验**（:136——UNBOUND_BYTES_LENGTH——**预期字节长度断言**（防损坏数据/截断——定长序列化的自校验））；**家族实现**（IntegerSerializer 等——大端定长编码（javadoc :44——"4-byte big-endian int"）；**复合序列化**（BoundarySerializer :43——**字段拼接编码**（boolean + 类名长度 + 类名 + 值——**长度前缀协议**）；**注册中心**（Serializers——SPI 加载——02 仓库模式）；**包装器**（HoldingValueRedisSerializerWrapper——持有值包装——**D09 相关**（历史 REQ D09：ValueHolder 双向缓存 RawValue identity equals 永久失效——待验证））
- **对比取舍**：**知识增量**：①**定长字节数组自校验**（:136——**序列化长度断言**（反序列化前验证长度——损坏检测）；②**长度前缀复合编码**（:52-62——字段 + 长度 + 内容的二进制协议——**自描述复合值**）；③**Redis 专业类型序列化族**（Geo/Point/Range/Weights——**SDR 命令参数的二进制化**（Redis 协议原生类型映射））
- **测试佐证**：serializer 测试族（95 测试中的大头——[补扫]）
- **my-xhs**：**该用没用（实证）**——my-xhs 用 Jackson JSON 序列化（RedisConfig.createJsonSerializer :126-149——08 仓库对照已实证）——**二进制定长序列化族无场景**（JSON 覆盖）；定长自校验模式可借鉴（协议场景）

### 包: `interceptor` + `event` + `beans` + `annotation`（批 2：22 文件）

#### KP-902 命令拦截 + 事件化（RedisCommandInterceptor SPI + EventPublishingRedisCommandInterceptor）（interceptor 6 + event 3 全列）

- **维度**：[分布式问题]（Redis 可观测）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：RedisCommandInterceptor SPI（beforeExecute/afterExecute/onError 三回调）、RedisMethodContext、ApplicationEventPublisher
- **需求**：**命令级拦截 + 事件化**——每条 Redis 命令执行前后插入自定义逻辑（慢查询/审计/写操作广播——历史 REQ 定位缺口 1）
- **参考实现**：**拦截 SPI**（RedisCommandInterceptor——beforeExecute/afterExecute/onError 三回调覆盖命令生命周期——REQ-001 产出 + **RedisConnectionInterceptor**（连接级拦截——拦截 SPI 的连接变体——同族））；**事件发布拦截器**（EventPublishingRedisCommandInterceptor :56——implements RedisCommandInterceptor + ApplicationEventPublisherAware——afterExecute（:87——成功才发布（failure 分支 :87-92）→ publishRedisCommandEvent（:97-99——**Spring 事件发布**（RedisCommandEvent——命令可观测）））；**事件载体**（RedisCommandEvent/RedisOperationEvent/RedisConfigurationPropertyChangedEvent :event 包 3 文件）
- **对比取舍**：**知识增量**：①**命令生命周期三回调**（before/after/error——**SPI 拦截的完整契约**）；②**成功才发布**（:87——afterExecute 的 failure 判断——**事件语义**（失败不发成功事件——vs 06 仓库 @After finally 缺陷的对照——本实现语义正确）；③**my-xhs 同名移植实证**（my-xhs zone/redis/EventPublishingRedisCommandInterceptor + RedisCommandEvent——**同名同结构**——跨仓库对应物）
- **测试佐证**：interceptor 测试族（[补扫]）
- **my-xhs**：**已用（实证 diff）**——`my-xhs-common/.../zone/redis/` 7 文件——**同构移植（接口名微调）**：diff 实证 my-xhs `implements RedisMethodInterceptor`（微球 RedisCommandInterceptor——**接口名重命名**）+ BEAN_NAME 前缀 myxhs（微球 microsphere:——**命名空间化**）+ @Slf4j 改写——**结构同构（afterExecute 成功才发布模式一致）非逐字同名**

#### KP-903 连接工厂代理 + Template 包装（RedisConnectionFactoryProxyBeanPostProcessor:74-90 + RedisTemplateWrapperBeanPostProcessor:23-45 + HoldingValue 包装处理器 2 + WrapperProcessor SPI）

- **维度**：[工程问题]（SDR 扩展）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：BeanPostProcessor、AOP 代理、RedisConnectionFactory/RedisTemplate
- **需求**：**无侵入包装 SDR 组件**——连接工厂 AOP 代理（拦截所有连接）+ Template 包装替换（HoldingValue 语义）
- **参考实现**：**连接工厂代理**（RedisConnectionFactoryProxyBeanPostProcessor :74——extends GenericBeanPostProcessorAdapter\<RedisConnectionFactory>——**每个 RedisConnectionFactory Bean 包装成 AOP 代理**（拦截层——命令拦截的挂载点））；**Template 包装**（RedisTemplateWrapperBeanPostProcessor :23——按 Bean 名替换 RedisTemplate/stringRedisTemplate（:36——Set.of("redisTemplate", "stringRedisTemplate")——**指定 Bean 替换**）+ HoldingValueRedisTemplateWrapperProcessor（WrapperProcessor SPI——持有值语义包装）
- **对比取舍**：**知识增量**：①**BPP 批量代理**（:74——泛型 BPP 适配器（03 仓库 2.5）+ AOP 代理——**组件级无侵入拦截**）；②**按名替换 Template**（:36——**精准替换**（只动指定 Bean 名））
- **my-xhs**：**已用（实证）**——my-xhs RedisTemplateWrapper/InterceptingRedisConnectionInvocationHandler——**包装机制同名移植**

### 包: `replicator` + `metadata` + `generator` + `dynamic` + 其余（批 3：44 文件）

#### KP-904 跨实例逻辑复制（RedisReplicatorConfiguration:55-243 + kafka producer/consumer 族 13 全列：RedisCommandReplicatedEvent/KafkaConsumerRedisReplicatorConfiguration/KafkaRedisReplicatorConfiguration/KafkaRedisReplicatorModuleInitializer/KafkaProducerRedisCommandEventListener/KafkaProducerRedisReplicatorConfiguration/RedisComandEventPartitioner/RedisCommandReplicator/RedisReplicatorInitializer/RedisReplicatorModuleInitializer/RedisInitializer/RedisInterceptorModuleInitializer/RedisModuleInitializer——注意 RedisComandEventPartitioner 拼写 Comand 缺 p——第 6 例拼写错误）

- **维度**：[分布式问题]（Redis 复制）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式]（逻辑复制） | **置信度**：High
- **前置**：Kafka、RedisConfigurationPropertyChangedEvent、生产者/消费者模式
- **需求**：**跨实例逻辑复制**——一个实例的 Redis 写操作经 Kafka 重放到另一实例（历史 REQ 定位缺口 2——"无法让一个实例的写操作在另一个实例重放"）
- **参考实现**：**复制配置**（RedisReplicatorConfiguration :55——implements ApplicationListener\<RedisConfigurationPropertyChangedEvent>（配置变更响应）+ **producer/consumer 双开关**（:128/:134——isRedisReplicatorConsumerEnabled/isRedisReplicatorEnabled——**可独立启用**）+ **domains 配置**（:184-187——`microsphere.redis.replicator.domains.{domain}.redis-templates = redisTemplate,stringRedisTemplate`——**按域指定复制的 Template**）；**Kafka 通道**（producer/consumer 族 6 文件——写事件经 Kafka 序列化传输 + 消费端重放）
- **对比取舍**：**知识增量**：①**逻辑复制 vs 物理复制**（Redis 主从物理复制 vs 应用层 Kafka 逻辑复制——**应用级重放的取舍**（可控/跨集群——但非事务一致）；②**按域配置复制范围**（:187——**粒度控制**（哪些 Template 参与复制））
- **my-xhs**：**该用没用（实证）**——my-xhs 无跨实例 Redis 复制（单 Redis 场景）；逻辑复制模式可借鉴（多活 Redis 双写——若未来需要）

#### KP-905 命令元数据仓库 + Doclet 编译期生成（SpringRedisMetadataRepository:69-203 + SpringRedisMetadataLoader:49 + SpringDataRedisMetadataGenerationDoclet:113-446 + 元数据族 6 全列：MethodInfo/MethodMetadata/ParameterMetadata/SpringDataRedisMetadataGenerator/RedisCommandUtils/RedisUtils + spring/metadata 2：SpringRedisMetadataLoader/SpringRedisMetadataRepository）

- **维度**：[工程问题]（元数据生成）| **权重**：[支撑] | **深度**：🟡 | **优先级**：P2 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：RedisCommands 命令接口族（SDR）、Javadoc Doclet API（ElementScanner9/DocTrees）、反射
- **需求**：**Redis 命令接口的元数据化**——接口名/方法名/参数类型 → 元数据仓库（拦截器按元数据识别命令）+ **Doclet 编译期自动生成**（免手写元数据）
- **参考实现**：**元数据仓库**（SpringRedisMetadataRepository :69——接口类名缓存（:80——**减少类加载开销**）+ 命令对象函数映射（:89）+ 按接口名/方法名/参数类型查 Method（:141）+ **写命令识别**（:183——isWrite 判定——**拦截器按读写分类的依据**）；**Doclet 生成器**（SpringDataRedisMetadataGenerationDoclet :113——implements Doclet——**Javadoc Doclet 扫描 Spring Data Redis 命令接口 → 生成元数据**（run :221 + generateSpringDataRedisMetadata :227 + **RedisCommandMethodVisitor extends ElementScanner9**（:446——**AST 元素扫描**）——**编译期元数据生成**（与 02 仓库配置元数据三阶段闭环同族——doclet 变体））
- **对比取舍**：**知识增量**：①**Doclet 代码生成**（:113——**用 javadoc 工具链生成代码/元数据**（编译期反射替代）——APT 之外的第二种编译期生成路径）；②**写命令识别**（:183——**命令读写分类**（拦截器事件语义的依据）
- **my-xhs**：**该用没用（实证）**——my-xhs 无命令元数据仓库（拦截器直接包装——无读写分类）

#### KP-906 配套族（Enable 注解 3：EnableRedisConfiguration/EnableRedisContext/EnableRedisInterceptor + condition 3：ConditionalOnRedisAvailable/ConditionalOnRedisEnabled/ConditionalOnRedisInterceptorEnabled + dynamic connection 2：DynamicRedisConnectionFactory/DynamicRedisConnectionFactoryCleanerListener + cloud 2：RedisCloudAutoConfiguration/PropagatingRedisConfigurationPropertyChangedEventApplicationListener + registrar 3：RedisConfigurationBeanDefinitionRegistrar/RedisContextBeanDefinitionRegistrar/RedisInterceptorBeanDefinitionRegistrar + wrapper 3：HoldingValueStringRedisTemplateWrapperProcessor/StringRedisTemplateWrapper/WrapperProcessors + constants 3：RedisConstants/RedisSpringUtils/SpringRedisCommandUtils + util 4 + context 4 + test 2：AbstractRedisTest/StandardOption + config 1 + ReporterLoggerAdapter + SpringDataRedisMetadataGenerator——全列）

- **维度**：[工程问题] | **权重**：[支撑] | **深度**：🟢 | **优先级**：P3 | **过时**：[时间无关模式] | **置信度**：Medium（归组）
- **前置**：@Enable 对称注解、条件注解
- **需求**：**配套归组**——@EnableRedisConfiguration/EnableRedisContext/EnableRedisInterceptor（:50/:49/:76——@Target TYPE+ANNOTATION_TYPE——**@Enable 对称三件**）+ 条件（condition 3）+ 动态连接（dynamic 2——RedisConnection 动态代理）+ cloud 适配（2）+ 工具/上下文/测试基座
- **my-xhs**：**已用（实证）**——my-xhs RedisInterceptorAutoConfiguration——**自动配置对应物**

### 包总结（redis 批 1-3）

- **核心命题**：**"Redis 基础设施增强四支柱"**——命令拦截事件化（KP-902）+ 跨实例复制（KP-904）+ 序列化家族（KP-901）+ 元数据生成（KP-905）——SDR 代理挂载（KP-903）
- **my-xhs 对应物实证**：zone/redis/ 族 7 文件 = 拦截 + 包装 + 自动配置的移植版——**本仓库是 my-xhs redis 族的上游**（06 仓库对照节反向印证）
- **历史 REQ 定位**：3 生产缺口（拦截/复制/监控）中拦截+复制已实现；监控（操作级）待验证

---

## 三、深度 review 七项报告（批 1-3 合并）

> 2026-08-13 批判性 review：穷尽性核对先行——86/86 生产文件全覆盖（骨架归组式——文件全列于 KP 归组）。

- [x] **① 源码行号精确核对**：KP-901:52/:87/:97/:136/:171、KP-902:56/:87/:97-99、KP-903:74/:23/:36、KP-904:55/:128/:134/:184-187、KP-905:69/:141/:183/:113/:446——全部 grep 实证 ✓
- [x] **② 穷尽性**：86/86 生产文件全覆盖（basename 脚本核对——铁律 #7）✓
- [x] **③ 空节标注**：KP-906 归组 Medium（未逐行深读）✓
- [x] **④ 过时三级**：6 KP 全部标注（均时间无关模式）✓
- [x] **⑤ 重复内容**：拦截族与 my-xhs zone/redis/ 对应物（KP-902/903/906 对照）；序列化家族与 02 仓库序列化 SPI（KP-901 归组）；replicator 与 Kafka 生态 ✓
- [x] **⑤b 引用目标核对**：RedisSerializer/RedisConnectionFactory/RedisCommands（spring-data-redis——本地依赖 jar 实证）✓；Doclet API（JDK11 javadoc——本地源码有）✓
- [x] **⑥ 诚实标注**：KP-906 Medium 归组；replicator Kafka 细节未逐行深读已标注 ✓
- [x] **⑦ 命名空间迁移**：N/A ✓

### 历史 REQ 交叉验证（10-redis 部分）

> 来源：`08-REQ-requirements-spec.md`（Redisson 视角规划——REQ-001~006 + D01-D08 + N01-N05）

| # | 历史断言 | 验证 | 落点 |
|---|---------|------|------|
| REQ-001 | 命令拦截 SPI（before/after/error 三回调） | ✅ 证实（RedisCommandInterceptor + EventPublishingRedisCommandInterceptor:56） | KP-902 |
| REQ-002 | 跨实例逻辑复制 | ✅ 证实（RedisReplicatorConfiguration:55 + Kafka 族） | KP-904 |
| REQ-003 | 操作级监控 | ⬜ 待验证（RedisOperationEvent 存在——监控完整性待深读） | KP-902 |
| D09 | ValueHolder 双向缓存 RawValue identity equals 永久失效 | ⬜ 待验证（HoldingValueRedisSerializerWrapper——批 1 未深读） | KP-901 |
| 定位修正 | 拦截点基于 SDR RedisConnection 代理（非 Redisson） | ✅ 证实（RedisConnectionFactoryProxyBeanPostProcessor:74 AOP 代理） | KP-903 |

**D01-D09 缺陷表验证（深度 review 轮）**：

| # | 历史断言 | 验证 | 实证 |
|---|---------|------|------|
| D01 | 拦截范围仅 SDR，Redisson 不可见 | ✅ 证实（源码无 Redisson 引用——grep 空） | KP-903 |
| D02 | Kafka 消费者无幂等/乱序/重试保障 | ⬜ 部分（架构事实——Kafka at-least-once 原生语义；消费者实现细节待深读） | KP-904 |
| D03 | 事件序列化单字节长度前缀 >255 截断 | ⬜ 部分（BoundarySerializer 用 **4 字节 Integer 长度前缀**（:59）——单字节前缀位置未找到——**历史断言位置待确认**） | KP-901 |
| D04 | 单参数命令 write 误判 | ⬜ 部分（isWrite 基于 MethodInfo :320——判定逻辑深读不足） | KP-905 |
| D05 | ThreadLocal 永远不清理 | ❌ **部分证伪**（clearTarget() :177 存在 `beanNameHolder.remove()`——**有清理 API 但非自动**（依赖调用方显式调用——无 finally 自动清理）——历史"永远不清理"过强） | KP-907 |
| D06 | 等值事件 equals/hashCode 契约违反 | ✅ 证实（:312 `Arrays.deepEquals(args)` vs :319 `Arrays.hashCode(args)`——**多维数组不对称**——deepEquals 相等但 hashCode 可不同——契约违反） | KP-902 |
| D07 | 异常传播被 NPE 覆盖 | ✅ 证实（:141 `throw e.getCause()`——cause null 时抛 NPE 吞原异常） | KP-902 |
| D08 | 元数据强依赖静态 YAML | ✅ 证实（main/resources/META-INF/spring-data-redis-metadata.yaml 存在——运行时 SnakeYAML 加载；doclet 生成器并存） | KP-905 |
| D09 | ValueHolder 双向缓存永久失效 | ❌ **历史自证伪**（v3 修正——record RawValue Arrays.equals 内容比较非 identity） | — |

**进度**：REQ-001/002 ✅ + D 表 4 证实（D01/D06/D07/D08）/ 3 部分（D02/D03/D04）/ 2 证伪（D05 部分/D09 历史自证伪）；REQ-003 监控待验证

---

## 四、my-xhs 落地判定汇总表（2026-08-13 实证版）

> my-xhs zone/redis/ 族 7 文件（06 仓库对照节已列）——本仓库为其上游来源。

| KP | 判定 | 说明 |
|----|------|------|
| KP-901 | 该用没用 | 二进制定长序列化族——my-xhs Jackson JSON 覆盖；定长自校验模式可借鉴 |
| KP-902 | **已用** | my-xhs EventPublishingRedisCommandInterceptor/RedisCommandEvent 同名移植（拦截事件化） |
| KP-903 | **已用** | my-xhs RedisTemplateWrapper/InterceptingRedisConnectionInvocationHandler 包装移植 |
| KP-904 | 该用没用 | 跨实例 Kafka 复制——my-xhs 无（多活 Redis 双写为差距） |
| KP-905 | 该用没用 | 命令元数据仓库/Doclet 生成——my-xhs 无读写分类 |
| KP-906 | **已用** | my-xhs RedisInterceptorAutoConfiguration 对应物 |

**汇总**：已用 3 / 该用没用 3。
**核心结论**：my-xhs 移植了**拦截/包装/自动配置**三支柱（redis 可观测）；**序列化/复制/元数据**为差距（JSON 覆盖/单 Redis 场景/无分类需求）。

### 深度 review 补充（问题域对照轮——历史 08 分析 3 大主题）

#### KP-907 问题域三主题（ThreadLocal 路由代价 + 双层代理架构 + Kafka 复制三陷阱 + V0/V1 版本化）

- **维度**：[分布式问题]（架构决策）| **权重**：[核心] | **深度**：🔴 | **优先级**：P1 | **过时**：[时间无关模式] | **置信度**：High
- **前置**：ThreadLocal 语义、JDK 动态代理、Kafka at-least-once、版本化序列化
- **需求**：**架构决策问题域**——动态连接的路由代价/双层代理的必要性/复制的一致性边界（历史 08 分析 3 大主题——查漏补缺轮）
- **参考实现**：**① ThreadLocal 路由代价**（DynamicRedisConnectionFactory :42——`ThreadLocal<String> beanNameHolder`——**按线程持有连接工厂 Bean 名**（动态连接的路由键）+ 跨线程传递问题（:163——注释"ThreadLocal cache redisConnectionFactoryBeanName 到 target thread"——**线程池场景需显式传递**——历史分析 :8-67 详述多租户矛盾（ThreadLocal 在多租户中的位置/为什么仍用））；**② 双层代理架构**（历史 :68-148——**第一层 AOP Proxy 包装 RedisConnectionFactory**（只拦 getConnection() :74——**替换返回值**）+ **第二层 JDK 动态代理包装 RedisConnection**（拦所有命令——before/after 拦截器链）——**为什么必须两层**：SDR 无替换连接的点（RedisConnection 几十个实现类 Jedis/Lettuce/Reactive——不可能每实现写代理子类——**第一层换实现、第二层统一包装**）；**"观察者不是守卫"限制**（:111——拦截器异常被 catch(Throwable) 吃掉——**拦截器定位为观察者（日志/事件/审计）非守卫（限流/熔断）**——若需求要守卫架构不支持 + handleError 默认空实现——**限流熔断失效数小时后才发现**）；**③ Kafka 复制三陷阱**（历史 :149-258——**无事务管道**（4 场景：源成功 Kafka 失败/重放失败/源超时实写入/重复消费——非幂等命令 INCR/LREM 重复重放**永久分歧**）+ **异步 send 静默丢失**（kafkaTemplate.send() 无 .get()——后台 Sender.run() 异常——**不知情的丢失比已知失败更危险**）+ 读一致性窗口（从目标读总可能旧）+ 事件循环；**④ V0/V1 版本化序列化**（RedisCommandEventSerializer :43-51——**VersionedRedisSerializer 枚举**——V0 字符串编码（接口名:方法名——几十字节）+ V1 整数索引（4 字节——**依赖生产/消费者 MethodMetadata 索引一致**——升级 spring-data-redis 后索引偏移——跨版本消息无法反序列化）——**版本字节写首位**（:51——自描述协议））
- **对比取舍**：**知识增量**：①**版本化序列化正反对照**（redis V0/V1 版本字节 :51 vs gateway G12 隐式 schema 无版本号——**同一生态两仓库的协议设计对照**（有版本 vs 无版本——跨仓库知识）；②**观察者 vs 守卫**（拦截器定位决策——**可观测与防护的架构边界**（限流熔断应走守卫路径——拦截器捕获异常语义）；③**复制一致性边界**（:149——**跨系统无事务的 4 场景清单**（Redis 不支持 XA/Kafka 不支持 2PC——一致性是设计者责任）+ at-least-once 与幂等命令交集（INCR/LREM 永久分歧）；④**异步 send 静默丢失**（无 .get() 的 Kafka 发送——**生产隐患模式**（异步 API 的失败吞噬））
- **my-xhs**：**该用没用（实证）**——my-xhs 无 Kafka 复制（无此陷阱）；**拦截器定位教训**（my-xhs 拦截器若做限流需守卫路径——观察者/守卫边界）；V0/V1 版本化模式可借鉴（协议设计）

### 包总结（问题域补充）

- **核心命题**：**"架构决策问题域"**——ThreadLocal 路由代价/双层代理必要性/复制一致性边界/V0-V1 版本化——历史 08 分析 3 大主题全映射
- **跨仓库对照**：V0/V1 版本化 vs gateway G12 无版本号——**协议设计正反例**
- **验证成果**：ThreadLocal :42/V0-V1 :43-51 源码实证；复制陷阱为架构分析（源码侧 KafkaTemplate.send 异步已确认——无 .get()）
