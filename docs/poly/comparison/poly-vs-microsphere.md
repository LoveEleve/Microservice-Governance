# PolyCache vs microsphere-redis 对比分析

> 目的：对照原版 microsphere-redis（小马哥）说明 PolyCache 做到了什么、差异在哪、还剩什么没做。
> 原版位置：`/data/workspace/source-code/microsphere/microsphere-redis`
> 本版位置：`/data/workspace/source-code/poly/polycache`

## 一、模块对照

| 原版模块（主类数） | PolyCache 对应 | 说明 |
|---|---|---|
| microsphere-redis-core（10） | polycache-core | 原版是 SDR 方法元数据 + `ValueHolder`；本版是命令目录 + 复制消息模型 |
| microsphere-redis-spring（54） | polycache-redisson（9） | 原版 SDR `RedisConnection` 代理拦截；本版 Redisson 管线捕获 + 命令重放 |
| microsphere-redis-replicator-spring（11） | polycache-replicator（5） | Kafka 生产/消费/重放 |
| microsphere-redis-spring-boot（3） | polycache-spring-boot（3） | 原版只有条件注解（无条件装配类）；本版是完整自动装配 |
| microsphere-redis-spring-cloud（2） | —（PolyConfig 可承接） | 配置变更事件跨节点传播 |
| microsphere-redis-spring-test（2） | —（自包含测试矩阵替代） | |
| microsphere-redis-generator（4，doclet） | —（不需要） | 命令级方案无需生成 SDR 方法→命令元数据 |
| parent / dependencies | 根 pom BOM | |

## 二、设计轴逐项对比

| 轴 | microsphere-redis（原版） | PolyCache（本版） |
|---|---|---|
| 拦截基座 | SDR 连接代理：`RedisConnectionFactoryProxyBeanPostProcessor` 把每个 `RedisConnectionFactory` 包成 AOP 代理，`getConnection()` 返回 JDK 代理，`InterceptingRedisConnectionInvocationHandler` 逐方法回调 | Redisson `NettyHook.afterChannelInitialization` 在每条连接管线尾部挂 `CommandCaptureHandler`，观察 `CommandData` |
| 拦截粒度 | **SDR 接口方法级**（`Method` + args，如 `RedisStringCommands.set(byte[],byte[])`） | **Redis 命令级**（命令名 + 参数，如 `SET k v`） |
| 事件模型 | Spring `ApplicationEvent`（`RedisCommandEvent` 携带 `Method`/`interfaceName`/`args`；`RedisOperationEvent` 供监控） | 纯 `RedisCommandMessage`（commandName + wire-ready byte[][] + key + source），Kafka 自定义二进制编解码 |
| 写判定 | 方法→Redis 命令映射（doclet 生成 `RedisMetadata`：interfaceName/methodName/parameterTypes/commands/write）+ `redis-write-commands` 名单 | 官方命令目录 + `ACL CAT write` 名单 + 追加 `EVAL`/`EVALSHA`（无需方法元数据） |
| 参数形态 | SDR 已序列化的 `byte[]`（RedisTemplate serializer 编码后进 `RedisConnection`） | Redisson codec 编码后的 `ByteBuf` / 文本 key（编码逻辑镜像 `CommandEncoder`） |
| 重放 | 反射调用远端 `RedisConnection` 的同名方法（`ReflectionUtils.invokeMethod`，args 直接复用） | `CommandAsyncExecutor.writeAsync(key, ByteArrayCodec, RedisCommand(宽松解码器), args)`，按 key 路由（3 节点真集群已验证） |
| 回环防护 | 重放前把代理连接**解包成 raw**（`getRawRedisConnection`）；未见 `source` 过滤（本机房消息是否回灌本地 Redis 未验证） | ①消息携带 `source`，消费端跳过本机房写入；②自动装配为重放创建**专用 Redisson 客户端**（克隆 Config、不装捕获） |
| 顺序 / 投递语义 | Kafka `BatchAcknowledgingMessageListener`（批处理后 ack） | 逐条等待重放完成后提交（分区内串行、保序）；默认 `earliest`；失败重试 3 次（1s 间隔）后告警跳过 |
| 批次 | SDR 批处理是连接方法（multi/exec 等），由代理按方法拦截 | 专门处理 Redisson `CommandsData`，含 **skipResult** 批次语义（实测修复：内层 promise 不完成时挂 batch promise） |
| 脚本 | SDR `scriptingCommands` 方法级拦截 | `SCRIPT LOAD` 学习脚本体（SHA1+LRU），`EVALSHA` **透明转换为 `EVAL`** 复制 |
| 动态连接 | `DynamicRedisConnectionFactory`：`ThreadLocal` 切换目标 factory + `RedisConfigurationPropertyChangedEvent` 热切换 + Web 清理监听 | ✅ 已对齐且更安全：`DynamicRedissonClient`（客户端门面 JDK 代理 + 线程级切换 + 目标增删/热替换 + 可切换默认目标；**作用域 API `runWithTarget`/`callWithTarget` 自动恢复、可嵌套**，从根上避免线程残留；原版依赖 Web 监听器兜底清理）。注：Redisson 3.27/3.35 的 `Config` 拷贝构造不复制自定义 `ConnectionManager`，`useCustomServers` 经 `Redisson.create` 失效（已实测），故路由在门面层实现 |
| 多 domain / 上下文 | `RedisContext` + `@EnableRedisContext`/`@EnableRedisConfiguration`/`@EnableRedisInterceptor`；Kafka topic = `topicPrefix + domain` | 单 topic + key 分区；多机房以 `source` 区分 |
| 拦截器扩展 | `RedisConnectionInterceptor` / `RedisCommandInterceptor` SPI 链（可插自定义） | ✅ 已对齐：`RedisCommandInterceptor` 捕获链 SPI（可过滤/改写/跳过消息），Spring Boot 自动收集 Bean 并按顺序执行 |
| 配置开关 | 大量 `@ConfigurationProperty`（`microsphere.redis.replicator.*`） | `polycache.replication.*` + 复用 `spring.kafka.*` / `spring.data.redis.*` |
| 外部依赖 | 依赖 6+ 个 microsphere 仓库（spring/java/configuration/annotation/reflect/logging…） | **零 microsphere 依赖**；仅 Redisson / Spring Kafka / Spring Boot |
| 测试 | `microsphere-redis-spring-test` 模块 + 常规单测 | 自包含矩阵：本地 redis-server 双实例、Embedded Kafka、**3 节点真集群**、并发、**Docker 双机房演示**、双机房全链路、动态路由 E2E（60 测试） |

## 三、本版相对原版的取舍（为什么不同）

1. **客户端换基座（SDR→Redisson）**：用户指定。换来的是命令级（不依赖 SDR 方法签名/元数据生成器/序列化器包）与 wire-ready 参数（跨语言、跨序列化器）。
2. **不需要 `ValueHolder`/`HoldingValue*`**：原版这套是给"方法级事件"服务的——在 serializer 层缓存对象↔字节映射，供拦截层还原参数；命令级方案直接复制字节，无需还原。
3. **不需要 `generator`（doclet）**：方法→命令元数据只对"方法级写判定"有用；命令级只需命令名（官方命令目录）。
4. **不需要 spring-test 模块**：用进程级真实依赖（redis-server / embedded Kafka）覆盖，更接近真实。
5. **增强的回环与投递语义**：原版靠解包代理防二次拦截、批处理 ack；本版额外做 `source` 过滤 + 专用重放客户端 + 逐条完成后提交 + `earliest` + 重试策略（都有测试作证）。

## 四、原版有、本版没有的能力（剩余深挖清单）

| 优先级 | 能力 | 原版对应 | 状态 |
|---|---|---|---|
| 高 | **动态连接热切换** | `DynamicRedisConnectionFactory` + 配置变更事件 + Web 清理监听 | ✅ 已完成：`DynamicRedissonClient`（见二、设计轴对比；待补：Web 请求结束自动清理、Spring Boot 属性化装配） |
| 中 | **拦截器 SPI** | `RedisConnectionInterceptor`/`RedisCommandInterceptor` | ✅ 已完成：`RedisCommandInterceptor` 链 + Boot Bean 自动收集 |
| 中 | **监控/可观测**：命令级事件、操作事件、指标 | `RedisOperationEvent` + 拦截器链 | 待做：Micrometer 复制量/延迟/丢弃/失败直方图 |
| 中 | **配置变更联动**：配置中心改 Redis 配置 → 热更新/传播 | spring-cloud 模块 + PolyConfig | 待做：已具备 `replaceTarget` 热替换，缺与 PolyConfig 事件的自动对接 |
| 低 | **多 domain/多上下文语义**（一套应用多组 Redis） | `RedisContext` + 注解 | 我们的多机房=多应用实例，按需再说 |
| 低 | **序列化器缓存/工具包** | serializer 包 20+ 类 | 命令级方案不需要 |

## 五、面试话术（一句话版）

> 原版 microsphere-redis 是"Spring Data Redis 连接代理 + 方法级事件 + Kafka 复制"，重放靠反射调用远端连接方法，写判定靠 doclet 生成的方法元数据，重放防回环靠解包代理。
> 我的 PolyCache 换成 Redisson 基座做**命令级复制**：Netty 管线捕获（不代理任何 API）→ 参数 wire-ready → Kafka 按 key 分区 → 远端 `CommandAsyncExecutor` 语义重放；回环防护是 `source` 过滤 + 专用重放客户端，投递是至少一次 + 分区内保序；并且实测覆盖了 skipResult 批次、EVALSHA 脚本、真集群拓扑、并发与 Docker 双机房。其中动态连接热切换与拦截器 SPI 已完成对齐（动态路由：客户端门面代理 + 线程级切换 + 目标热替换），剩下的主要缺口是监控指标（Micrometer）与配置中心联动。

## 六、备注（未完全验证的原版行为）

- 原版消费端是否会把本机房产生的消息回灌本地 Redis（无 `source` 过滤的推测）未实测验证。
- 原版 SDR 批处理（RBatch）在代理层的具体拦截形态未实测。
- 原版对 `EVALSHA`（useScriptCache）的复制行为未实测。
