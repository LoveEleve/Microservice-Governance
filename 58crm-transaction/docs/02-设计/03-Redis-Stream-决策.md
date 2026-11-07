# 决策：Redis Stream 作为消息传输层

> 状态：已采纳，替代 Redis List 作为生产传输层。  
> 范围：只用于事务消息投递，不改变 MySQL 是正本、TCC 状态在 MySQL 的边界。

## 1. 为什么改

Redis List 的缺口：

```text
没有 ACK
没有 pending
没有消费组 offset
消费者 BLPOP 后崩溃，只能靠生产方猜
```

为了补 ACK，之前设计了：

```text
消费方 Inbox + lease
生产方 SENT 超时重投
批量 ACK RPC
```

这套能工作，但复杂：

```text
消费方要自己维护 lease
生产方要维护 ACK 接口
成功也要回写状态
重复投递和 ACK 竞态要处理
```

Redis Stream 原生提供：

```text
XADD
XGROUP
XREADGROUP
XACK
XPENDING
XCLAIM / XAUTOCLAIM
```

所以采用 Stream，消掉生产方 ACK RPC 和消费方自造 pending。

## 2. 业务语义

```text
每个业务线一个 Stream：
  tx:stream:{bizCode}

每个业务线一个消费组：
  tx-group-{bizCode}

一个逻辑消费者组
不做多下游 fan-out
需要多个下游 → 上游拆多条事件
```

消费组在这里是“ACK/offset 机制”，不是“同一事件广播给多个下游”。

## 3. Kafka 映射

| Kafka | Redis Stream |
|---|---|
| topic | `tx:stream:{bizCode}` |
| partition | 业务线 `bizCode` |
| consumer group | `tx-group-{bizCode}` |
| offset | Stream entry ID / group last-delivered-id |
| ACK | `XACK` |
| pending | PEL |
| rebalance | consumer 组内竞争 |
| `linger.ms` | 10ms 发送窗口 |
| `batch.size` | 单批 256 |
| `buffer.memory` | 有界 OutboxBuffer |
| `Sender` | OutboxSender |

## 4. 生产方状态

```text
INIT       MySQL 已写，尚未 XADD
SENT       已 XADD 到 Stream
DEAD       XADD 重试超限
```

生产方不再维护：

```text
CONSUMED
消费 ACK RPC
```

消费确认由 Stream 的 `XACK` 和消费方 `t_consume_record` 维护。

## 5. 消费方状态

```text
t_consume_record
  msg_id PK
  biz_id
  stream_entry_id
  consumer_id
  status: READY / PROCESSING / CONSUMED / DEAD
  owner_token / owner_epoch / claim_count
  lease_until
  retry_count
  payload / payload_ref
```

流程：

```text
XREADGROUP
→ 写 t_consume_record(PROCESSING)
→ 业务处理
→ 本地事务提交
→ t_consume_record(CONSUMED)
→ XACK（可批量）
```

崩溃：

```text
XREADGROUP 后未 ACK
→ 消息在 PEL
→ XPENDING 查 pending
→ XCLAIM/XAUTOCLAIM 转移
→ 新 consumer 重试
```

## 6. Redis 版本约束

历史 58CRM 参数写的是 Redis 5.x，因为那是 2025 年的项目环境。  
当前新建项目的基线应为 Redis 7.x；本机验证版本：

```text
redis-server v=7.0.15
```

在这个版本上：

```text
XADD
XGROUP
XREADGROUP
XACK
XPENDING
XCLAIM
XAUTOCLAIM
```

都可用。

目标版本：

```text
Redis 7.x+
XADD pipeline
XREADGROUP COUNT 500
XACK batch
XAUTOCLAIM min-idle
```

如果部署环境仍是 Redis 5.x：

```text
XAUTOCLAIM 不可用
→ XPENDING + XCLAIM
```

不能把 Redis 5 当作新项目默认基线。

## 7. 可靠性兜底

```text
MySQL 仍是正本
Stream XADD 失败 → INIT 重试
Stream 丢数据 → 从 MySQL 重放
消费失败 → PEL + XCLAIM
消费成功 → XACK
重复消费 → msg_id 幂等
长时间 pending → 告警
Redis 内存 → Stream 长度/内存水位
```

## 8. 不能做的事

```text
不能 XADD 前就标 SENT
不能 XACK 前就标 CONSUMED
不能 trim 掉未 ACK 的 PEL
不能用一个 Stream 做多下游广播
不能把 Stream 当 MySQL 正本
```

## 9. 代码重构

```text
OutboxScanner
  → 扫 MySQL INIT
  → 放入有界 OutboxBuffer

OutboxSender
  → 按 bizCode 取 batch
  → 10ms 窗口
  → XADD pipeline
  → 标记 SENT

StreamConsumer
  → XREADGROUP COUNT 500
  → 写 t_consume_record
  → 业务处理
  → XACK batch

PendingRecovery
  → XPENDING
  → XCLAIM
  → 重试/DEAD
```

替换：

```text
RedisListQueueAdapter → RedisStreamAdapter
MessageAckService → 删除（ACK 走 XACK）
```

## 10. 运维兜底

```text
XPENDING 数量告警
单条消息 delivery count 告警
consumer lag 告警
Stream 长度/内存告警
XADD 失败率告警
Redis 主从切换演练
kill consumer 演练
Redis 7 XAUTOCLAIM 演练
Redis 5 XCLAIM 兼容演练（仅旧环境）
```

团队没用过不是不能做，前提是：

```text
PEL 可观测
XCLAIM 可演练
MySQL 可重放
消费幂等可证明
```

缺一条都不算兜底。
