# Starter 类与方法全解

> 业务方先看：`docs/01-使用/03-Starter接入手册.md`。  
> 本文给框架开发者看 Starter 的装配细节。

## 1. 模块

```text
crm-tx-spring-boot-starter
├── CrmTxProperties
├── CrmTxAutoConfiguration
├── EnableCrmTx
└── META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## 2. `CrmTxProperties`

配置前缀：

```text
crm.tx
```

字段：

```text
enabled
autoStart
streamPrefix
bizCodes
outbox
```

方法：

| 方法 | 作用 |
|---|---|
| `isEnabled/setEnabled` | 是否装配 Starter |
| `isAutoStart/setAutoStart` | Producer 是否自动 start |
| `getStreamPrefix/setStreamPrefix` | Stream key 前缀 |
| `getBizCodes/setBizCodes` | bizCode 白名单 |
| `getOutbox/setOutbox` | 生产参数 |
| `toRegistry()` | List -> BizCodeRegistry |
| `toOutboxConfig()` | YAML 参数 -> OutboxConfig |

嵌套 `Outbox` 字段：

```text
linger
batchSize
bufferCapacity
bufferCapacityBytes
scanBatch
scanInterval
retryAfter
maxRetries
senderThreads
```

每个字段都有：

```text
getXxx()
setXxx()
```

## 3. `CrmTxAutoConfiguration`

### `crmTxBizCodeRegistry`

```java
return properties.toRegistry();
```

结果：

```text
BizCodeRegistry Bean
空 biz-codes -> 启动失败
重复 bizCode -> 启动失败
```

### `crmTxOutboxConfig`

```java
return properties.toOutboxConfig();
```

结果：

```text
OutboxConfig Bean
非法参数 -> 启动失败
```

### `crmTxOutboxBuffer`

```text
按 OutboxConfig 的条数/字节容量创建 buffer
```

### `crmTxOutboxInFlight`

```text
创建 in-flight 去重集合
```

### `crmTxInterceptorChain`

```java
new OutboxInterceptorChain(interceptors)
```

结果：

```text
收集业务方提供的 OutboxInterceptor
没有则空链
```

### `crmTxPartitioner`

```text
默认 BizCodePartitioner
```

### `crmTxSerializer`

```text
默认 DefaultOutboxSerializer
```

### `crmTxLocalMessageRepository`

条件：

```text
存在 LocalMessageMapper Bean
```

结果：

```text
MybatisLocalMessageRepository
```

### `crmTxOutboxPublisher`

条件：

```text
存在 LocalMessageMapper
存在 BizCodeRegistry
```

结果：

```text
MybatisOutboxPublisher
```

### `crmTxRedisStreamPublisher`

条件：

```text
存在 StringRedisTemplate
```

结果：

```text
RedisStreamPublisherAdapter
```

### `crmTxOutboxProducerAutoStart`

条件：

```text
RedisStreamPublisher
LocalMessageRepository
crm.tx.auto-start=true / 未配置
```

结果：

```text
OutboxProducerFactory.create(...)
producer.start()
```

### `crmTxOutboxProducerManualStart`

条件：

```text
crm.tx.auto-start=false
```

结果：

```text
OutboxProducer 已创建但未 start
业务方自己调用 start()
```

## 4. `EnableCrmTx`

```java
@Target(ElementType.TYPE)
@Retention(RUNTIME)
@Import(CrmTxAutoConfiguration.class)
public @interface EnableCrmTx {
}
```

作用：

```text
显式导入自动配置
```

## 5. 条件装配

```text
crm.tx.enabled=false -> 整个配置不生效
缺少 LocalMessageMapper -> 不创建 repository/publisher
缺少 StringRedisTemplate -> 不创建 StreamPublisher
缺少 RedisStreamPublisher/LocalMessageRepository -> 不创建 Producer
```

## 6. 覆盖默认 Bean

所有 Bean 都有：

```java
@ConditionalOnMissingBean
```

业务方可以自己提供：

```text
OutboxInterceptor
OutboxPartitioner
OutboxSerializer
OutboxConfig
BizCodeRegistry
LocalMessageRepository
RedisStreamPublisher
```

## 7. 业务方最小依赖

```text
LocalMessageMapper
StringRedisTemplate
DataSource / 事务管理器
```

业务方不需要：

```text
new OutboxBuffer
new Scanner
new Sender
new Producer
```
