# C-002.2：Stream 拉取与持久化

## 用户故事

作为消费方服务，我需要从 Redis Stream 批量拉取消息并可靠落库到 `t_consume_record(READY)`，这样 Worker 崩溃前消息不会因为内存丢失而消失。

## 范围

```text
StreamSource 抽象
RedisStreamSource
StreamQueue / BoundedStreamQueue
StreamPuller
ConsumePersister
```

## Out of Scope

```text
ConsumerWorker
AckBatcher
LeaseWheel
PendingRecovery
ConsumerBootstrap
Spring Boot Starter 装配
```

## 验收条件

```text
AC1 StreamPuller 使用 XREADGROUP GROUP group consumer COUNT count BLOCK block STREAMS stream >
AC2 拉取后只入有界队列，不写 DB
AC3 队列满时 Puller 阻塞，不丢消息
AC4 ConsumePersister 批量取消息并 INSERT t_consume_record(READY)
AC5 重复 msg_id 落库仍由 INSERT IGNORE 幂等
AC6 Persister 失败不 ACK，消息保留在 PEL
AC7 停止 Puller 能中断阻塞读取并退出
AC8 坏消息（payload 和 payloadRef 都为空）不进入 READY，记录错误
```

## 边界情况

```text
Redis 暂时不可用
group 不存在
stream 不存在
队列满
停止信号
消息字段缺失
重复 XREADGROUP
```

## 非功能需求

```text
一个业务线一个 Puller
一个业务线一个有界队列
批量 COUNT 500
BLOCK 1000ms
不逐条写 DB
不 XACK
mysql 正本
```

## 设计约束

```text
Puller 不写 DB
Persister 不 XACK
XACK 只在 Worker 事务成功之后
Puller 通过 queue 背压
PEL 是 Puller 后崩溃的恢复来源
```

## 契约

```java
public interface StreamSource {
    List<ConsumedMessage> read(String stream, String group, String consumer,
                               int count, Duration block);
}

public interface StreamQueue {
    void putAll(List<ConsumedMessage> messages) throws InterruptedException;
    List<ConsumedMessage> takeBatch(int max, Duration timeout) throws InterruptedException;
    int size();
    void clear();
}

public final class StreamPuller implements AutoCloseable {
    public void start();
    public void close();
}
```

## 测试策略

```text
单元：
  queue 批量/超时/容量
  persister 批量落库
  puller 读取 -> queue
  puller 停止
  bad message 拒绝

集成：
  Redis Stream XREADGROUP 留到 C-002.4
```

## 验收用例

```text
queue_takeBatch_returnsUpToMax
queue_putAll_blocksWhenFull
persister_persistsBatchAsReady
persister_emptyBatch_doesNothing
puller_readsBatchAndEnqueues
puller_stopInterruptsBlockingRead
```
