# Outbox 发送重构：Kafka 风格 + Redis Stream

> 目标：把“扫库后当场发送”改成“扫描线程 + 有界 buffer + 独立 sender”，并保留 Kafka 式扩展点。

## 1. 目标结构

```text
OutboxScanner
  → OutboxBuffer
  → OutboxSender
      → OutboxInterceptorChain.onSend
      → OutboxPartitioner
      → OutboxSerializer
      → RedisStreamPublisher
      → OutboxInterceptorChain.onAcknowledgement
  → LocalMessageRepository.markSentBatch
```

## 2. Kafka 映射

| Kafka | 本项目 |
|---|---|
| `RecordAccumulator` | `OutboxBuffer`，按 bizCode 分桶，有界 |
| `ProducerBatch` | `OutboxBatch` |
| `Sender` | `OutboxSender` |
| `linger.ms` | 10ms `linger` |
| `batch.size` | 256 条 |
| `buffer.memory` | buffer 总容量 |
| `ProducerInterceptor` | `OutboxInterceptor` |
| `Partitioner` | `OutboxPartitioner` |
| `Serializer` | `OutboxSerializer` |
| topic/partition | `tx:stream:{bizCode}` |

## 3. 状态

生产方：

```text
INIT → SENT → DEAD
```

生产方不保存 `CONSUMED`，消费确认由 Stream `XACK` 和消费方 `t_consume_record` 负责。

## 4. 扫描

```text
定时扫描 INIT
  → buffer.offer
  → inFlight 去重
```

约束：

```text
buffer 满 → scanner 阻塞
不能入队后立刻标 SENT
发送成功后才 markSentBatch
崩溃后 inFlight 丢失，MySQL 仍可重扫
```

## 5. 发送

```text
sender 从 buffer 取第一批
→ 等待 linger（10ms）
→ 最多取 batchSize 条
→ 按 bizCode 分组
→ interceptor.onSend
→ partitioner.route
→ serializer.serialize
→ XADD pipeline
→ interceptor.onAcknowledgement
→ markSentBatch
```

失败：

```text
XADD 失败 → 不标 SENT，重试/回 buffer
进程崩溃 → MySQL INIT 可重扫
重复 → 消费方 XACK + msgId 幂等
```

## 6. Redis Stream

```text
stream: tx:stream:{bizCode}
group:  tx-group-{bizCode}
```

生产：

```text
XADD tx:stream:{bizCode} * msgId ... 
XGROUP CREATE ... MKSTREAM（幂等）
```

消费：

```text
XREADGROUP GROUP tx-group-{bizCode} consumer COUNT 500 BLOCK ...
XACK batch
XPENDING
XAUTOCLAIM
```

Redis 版本：

```text
7.x：XAUTOCLAIM
5.x：XPENDING + XCLAIM
```

## 7. 扩展点

```java
public interface OutboxInterceptor {
    OutboxRecord onSend(OutboxRecord record);
    void onAcknowledgement(SendResult result, Throwable error);
    default void close() {}
}

public interface OutboxPartitioner {
    String route(OutboxRecord record);
}

public interface OutboxSerializer {
    Map<String, String> serialize(OutboxRecord record);
}
```

扩展用途：

```text
traceId 注入
租户隔离
schema 版本
加密
采样
审计
指标
灰度
```

## 8. 测试

```text
Buffer：
  按 bizCode 分桶
  容量上限
  满时阻塞/背压

Interceptor：
  顺序
  修改 record
  异常隔离

Sender：
  10ms 窗口
  单批上限
  按 bizCode 分组
  XADD 失败不标 SENT

状态：
  INIT -> SENT
  SENT 重投 -> DEAD
  崩溃后重扫

事务：
  无事务拒绝
  回滚无消息
  提交有消息
```

## 9. 删除

```text
RedisListQueue
RedisListQueueAdapter
OutboxRelay
OutboxRelayScheduler
MessageAck
MessageAckService
LocalMessageAckService
生产方 CONSUMED
```
