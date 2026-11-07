# C-002.3：消费者 Worker、租约与 ACK

## 用户故事

作为消费方服务，我需要把 READY 消息交给业务 handler，并在业务事务成功后标记 CONSUMED、批量 XACK；长任务需要自动续约，崩溃后可以被新 owner 接管，旧 owner 不能提交。

## 范围

```text
C-002.3a 租约基础设施
  LeaseTask
  InFlightRegistry
  LeaseWheel
  NettyLeaseWheel
  LeaseRenewer
  LeaseManager

C-002.3a ACK 基础设施
  AckCommand
  StreamAcknowledger
  RedisStreamAcknowledger
  AckBatcher

C-002.3b Worker
  ConsumerWorker
  TransactionOperations 事务边界
  READY -> PROCESSING -> CONSUMED
```

## Out of Scope

```text
ConsumerBootstrap / XGROUP CREATE
PendingRecovery / XAUTOCLAIM
Spring Boot Starter 装配
真实 Redis/MySQL 集成测试
多实例压测
```

## 验收条件

```text
AC1 in-flight 按 msgId + ownerToken 唯一登记
AC2 Netty 时间轮按延迟触发
AC3 完成/取消后不再触发
AC4 续约成功重新 schedule
AC5 续约失败或超过 maxDeadline 停止续约并告警
AC6 时间轮回调只入续约队列，不直接写 DB
AC7 AckBatcher 按 stream 分组、按 batch size/超时 flush
AC8 XACK 失败保留 PEL，不抛业务事务
AC9 Worker 只在业务事务提交后加入 ACK 队列
AC10 fencing 失败时业务事务回滚
AC11 Worker 失败按 retry 规则回 READY/DEAD
```

## 边界情况

```text
重复 msgId 不同 owner
旧 owner 完成任务但已被接管
时间轮任务被取消后仍触发
续约时 lease 已过期
ACK 队列满
Redis XACK 部分成功
业务 handler 抛异常
```

## 设计约束

```text
时间轮只做触发，不做 DB
续约由独立线程批量执行
续约必须带 owner_token + owner_epoch
ACK 必须在业务事务提交之后
CONSUMED 与业务表同事务
PEL 由 XACK 清理
```

## 测试策略

```text
单元：
  InFlightRegistry
  NettyLeaseWheel
  LeaseManager
  AckBatcher
  ConsumerWorker 状态迁移

集成：
  C-002.4 真实 Redis + MySQL
```
