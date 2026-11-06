# C-001：可靠消息闭环

## 用户故事

作为业务服务，我希望业务事务提交后消息一定可重放；Redis 丢失或消费者崩溃时，消息仍然能从 MySQL 恢复。

## 范围

```text
本地消息表写入
Outbox Relay 扫描
Redis List 投递
消费幂等
状态回写
重试/租约
```

## Out of Scope

```text
TCC 协调器
参与方 barrier
线索分配业务
对账管理台
真实 64 库部署
Nacos/Sentinel 真实集群
```

## 验收条件

```text
AC1 业务事务成功 → t_local_message(INIT)
AC2 业务事务失败 → 没有消息
AC3 Relay 扫描 → LPUSH 业务线 List → SENT
AC4 消费成功 → 消费方 t_consume_record(PROCESSING) → CONSUMED
AC5 消费者崩溃 → 消费方 lease 过期重试；生产方 SENT 超时重投
AC6 重复投递 → t_idempotent 拦截
AC7 Redis 丢消息 → MySQL 可重放
AC8 同一事件只进一个业务线 List，不 fan-out
```

## 边界情况

```text
业务提交后 Relay 未启动
Relay 已 LPUSH，状态未更新 SENT
消费者取走消息后崩溃
消费成功，状态回写失败
Redis 重启丢队列
同一 msgId 重复投递
同一 eventKey 重复发布
payload 超过阈值，改存 payload_ref
```

## 非功能需求

```text
生产形态：64 分片、读写分离、Redis Cluster、Nacos、Sentinel
代码不允许单库分支
消息/幂等 SQL 必须带分片键
状态扫描不能全表扫描
```

## 设计约束

```text
MySQL 是正本
Redis List 只做可重放缓冲
一条事件一个业务线 List
一个逻辑消费者
不使用消费者组

生产方 t_local_message：INIT/SENT/DEAD
消费方 t_consume_record：PROCESSING/CONSUMED/DEAD
消费租约和 consumer_id 只在消费方库
消费方不远程写生产方库
```

## 契约

```java
public interface OutboxPublisher {
    void publish(BusinessEvent event);
}

public interface MessageHandler {
    void handle(BusinessMessage message);
}
```

生产方状态：

```text
INIT → SENT → DEAD
           |
           +-> SENT（超时重投）
```

消费方状态：

```text
PROCESSING → CONSUMED / DEAD
      |
      +-> PROCESSING（lease 过期重试）
```

## 测试策略

```text
单元测试：
  状态迁移
  幂等
  租约
  重试

集成测试：
  MySQL Outbox → Relay → Redis List → Consumer

故障测试：
  Redis 丢消息
  消费者崩溃
  重复投递
  状态回写失败
```

## 进度

```text
C-001.1 消息模型 + Mapper + OutboxPublisher：已完成
C-001.2 OutboxRelay + Redis List + 批量 ACK：已完成
C-001.3 MessageConsumer + 消费方 Inbox + 幂等：未开始
```

## 验收用例

```text
businessCommitSuccess_writesOutbox
businessCommitFailure_writesNoMessage
relayInitMessage_pushesToBizLineList
consumerSuccess_marksConsumed
consumerCrash_reclaimsExpiredLease
duplicateMessage_isIdempotent
redisLoss_replaysFromMysql
event_goesToSingleBizLineQueue
```
