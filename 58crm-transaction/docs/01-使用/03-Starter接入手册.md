# Spring Boot Starter 接入手册

> 读完你能做什么：业务方只加依赖、加配置、加 `@EnableCrmTx`，就能注入 `OutboxPublisher`。

## 1. 依赖

```xml
<dependency>
    <groupId>com.wuba.crm</groupId>
    <artifactId>crm-tx-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

前提：业务应用已经有：

```text
DataSource
MyBatis-Plus LocalMessageMapper
StringRedisTemplate
PlatformTransactionManager
```

## 2. 启动类

```java
@SpringBootApplication
@EnableCrmTx
public class LeadApplication {
    public static void main(String[] args) {
        SpringApplication.run(LeadApplication.class, args);
    }
}
```

说明：

```text
@EnableCrmTx 可选
Boot 3 也会通过 AutoConfiguration.imports 自动装配
显式注解用于团队需要一眼看到依赖的场景
```

## 3. 配置

```yaml
crm:
  tx:
    enabled: true
    auto-start: true
    stream-prefix: "tx:stream:"
    biz-codes:
      - lead
      - quota
    outbox:
      linger: 10ms
      batch-size: 256
      buffer-capacity: 50000
      buffer-capacity-bytes: 67108864
      scan-batch: 1000
      scan-interval: 10ms
      retry-after: 30s
      max-retries: 5
      sender-threads: 8
```

开关：

```text
crm.tx.enabled=false
  整个 Starter 不装配

crm.tx.auto-start=false
  装配 OutboxProducer，但不启动线程
  业务方自己调用 producer.start()
```

## 4. 业务方注入

```java
@Service
public class LeadAssignService {

    private final LeadRepository leadRepository;
    private final OutboxPublisher outboxPublisher;

    public LeadAssignService(
            LeadRepository leadRepository,
            OutboxPublisher outboxPublisher) {
        this.leadRepository = leadRepository;
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional
    public void assign(long leadId, long salesId, String requestId) {
        leadRepository.assign(leadId, salesId);

        outboxPublisher.publish(new BusinessEvent(
            "lead-assigned:" + leadId + ":" + requestId,
            "lead",
            String.valueOf(leadId),
            "LEAD_ASSIGNED",
            "{\"leadId\":" + leadId + ",\"salesId\":" + salesId + "}"
        ));
    }
}
```

业务方不需要 new：

```text
OutboxBuffer
OutboxScanner
OutboxSender
OutboxProducer
MybatisOutboxPublisher
```

## 5. Starter 自动装配了什么

```text
BizCodeRegistry
OutboxConfig
OutboxBuffer
OutboxInFlight
OutboxInterceptorChain
OutboxPartitioner
OutboxSerializer
LocalMessageRepository
OutboxPublisher
RedisStreamPublisher
OutboxProducer
```

业务方需要提供：

```text
LocalMessageMapper
StringRedisTemplate
```

## 6. 覆盖默认实现

Starter 使用 `@ConditionalOnMissingBean`，业务方可以自己提供：

```java
@Bean
public OutboxInterceptor auditInterceptor() {
    return new AuditOutboxInterceptor();
}

@Bean
public OutboxPartitioner customPartitioner() {
    return new MyPartitioner();
}
```

覆盖后 Starter 不会重复创建同名默认 Bean。

## 7. 业务方必须遵守

```text
publish 必须在 @Transactional 业务方法里
eventKey 必须稳定唯一
bizCode 必在 crm.tx.biz-codes 白名单
不要把 payload 只放 Redis
不要自己启动 Scanner/Sender
```

## 8. Starter 不负责

```text
不创建业务表
不创建 LocalMessageMapper
不创建 Redis 连接
不创建 DataSource
不做消费端
```

## 9. 测试

```bash
mvn -pl crm-tx-spring-boot-starter -am test
```

## 10. 一句话

```text
Starter 负责装配生产端
业务方只负责写业务表 + publish 事件
```
