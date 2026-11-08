# 简历项目描述：TCC 与消息组件

> 先按真实实现写，不夸大；面试前按学习进度补充“我亲手改过/验证过”的细节。

## 1. 项目名称

```text
58CRM 跨服务一致性框架：TCC + Outbox + Redis Stream
```

## 2. 一句话

```text
基于 TCC 与 Outbox 的跨服务一致性框架，解决线索分配、额度预占等场景下的分布式事务与可靠消息问题。
```

## 3. TCC 部分

```text
独立 Coordinator：Begin/Register/Report/Commit/Rollback/Query
gRPC 控制面 + gRPC RM callback，Nacos 注册 gRPC 实例
global/branch 状态机 + owner lease + fencing
TccParticipantService barrier：幂等、空回滚、悬挂
Confirm 本地事务：业务终态 + barrier + Outbox
Scheduler：next_retry_at、retry_count、退避、DEAD/FAILED
```

可写 bullet：

```text
设计并实现独立 TCC Coordinator，基于 MySQL 状态机、global owner lease 和 branch fencing，支持多实例安全推进。
设计 Participant barrier 与 Confirm/Cancel 幂等机制，覆盖空回滚、悬挂和重复回调。
实现 TCC 二阶段可靠性：DB 事务只推进状态，RM 回调在事务外执行；失败由 Scheduler 持久化重试。
实现 Confirm 与 Outbox 同本地事务，避免业务成功但终态消息丢失。
使用 Nacos 注册 gRPC 实例，TM 通过 OpenFeign 调业务 Try，TC 通过 gRPC callback 调 RM。
```

## 4. 消息部分

```text
MySQL Outbox + Redis Stream
Scanner / Buffer / Sender / Pipeline
Consumer Group / XREADGROUP / PEL / XACK / XAUTOCLAIM
t_consume_record / t_idempotent / lease + time wheel
```

可写 bullet：

```text
实现基于 MySQL Outbox + Redis Stream 的可靠消息组件，覆盖生产、投递、消费确认和 PEL 恢复。
实现消费租约、时间轮续约、幂等消费和死信恢复，保证消费者崩溃后的可恢复性。
完成多消费者/多 Stream/多 JVM 压测，本机最高约 700 msg/s，并修复批量写入死锁。
```

## 5. 验证

```text
单测 213+
真实 E2E：Nacos + MySQL + coordinator + lead + quota
成功：CONFIRMED
失败：CANCELLED + barrier SUSPENDED
```

## 6. 面试要能展开

```text
为什么 Try 不由 TC 发起
为什么 TC 不发现 RM
为什么 Confirm 的 Outbox 由业务写
owner lease 怎么防止旧 TC 覆盖
空回滚和悬挂怎么处理
retryable 为什么不立刻无限重试
Nacos 为什么只注册 TC
```

## 7. 真实边界

```text
没有 Sentinel/Prometheus
没有多 TC 故障注入
没有真实生产集群
消息吞吐是本机压测，不是生产容量
```
