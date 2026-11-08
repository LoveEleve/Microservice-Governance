# 58CRM 消息服务面试思维导图

> 基线：`crm-tx-outbox` + `crm-tx-consumer` 当前仓库实现。
> 范围：Outbox 生产、Redis Stream、消费组、PEL、租约/时间轮、幂等、故障恢复、参数与面试题。
> 用法：标题层级即节点层级，可直接导入支持 Markdown 的思维导图工具。
> 口径：MySQL 是正本，Redis Stream 是传送带；本机压测约 700 msg/s，不宣称生产 8W QPS。

## 0. 30 秒总览

### 0.1.1 消息服务解决什么问题？

- 核心结论：解决“业务已经成功，但下游事件没有可靠送达”的问题。
- 核心原理
  - 业务表更新和消息记录写在同一个 MySQL 本地事务。
  - 后台 relay 把消息投递到 Redis Stream。
  - 消费者用消费组 + PEL + 幂等消费，失败可恢复。
- 关键机制
  - `OutboxPublisher` 只写 MySQL，不直接写 Redis。
  - `OutboxScanner/Sender` 异步投递。
  - `XREADGROUP + XACK + PEL` 保证至少一次消费。
- 面试要点
  - 一句话：先可靠落库，再异步搬运；Redis 丢了可以从 MySQL 重放。

### 0.1.2 三条红线

```text
1. MySQL 决定消息是否存在、是否要重试
2. Redis 只决定现在传得快不快
3. 一条事件只进一个业务线 Stream，只被一个消费组消费
```

### 0.1.3 当前可验证事实

```text
生产端：OutboxPublisher + Scanner + Buffer + Sender
消费端：Puller + Persister + Worker + AckBatcher
恢复：PEL + XPENDING/XCLAIM + 租约 + 时间轮
本机压测约 700 msg/s
单测/集成测试全绿
```

### 0.1.4 明确不宣称

```text
不宣称生产 8W QPS
不宣称 Redis Cluster 多分片实测
不宣称消费端完全 exactly-once
不做多下游 fan-out
不做跨机房容灾
```

## 1. 背景与边界

### 1.1.1 为什么不用“业务提交后直接发 MQ”？

- 核心结论：业务提交和 MQ 发送是两次独立操作，无法原子。
- 核心原理
  - 业务提交成功，MQ 发送失败 -> 消息丢。
  - MQ 发送成功，业务回滚 -> 幽灵消息。
- 关键机制
  - Outbox 把“业务事实”和“消息记录”放进同一个本地事务。
  - 发送失败只是延迟，不是丢失。
- 面试要点
  - 不是 MQ 不可靠，而是“业务提交 + 发送”不可原子。

### 1.1.2 Outbox 与 TCC 的关系

```text
TCC：跨服务资源预留/确认/取消
Outbox：业务事实发生后的可靠通知
```

关系：

```text
TCC Confirm 里写业务终态 + Outbox
Outbox 不参与 Try/Confirm/Cancel 决策
```

### 1.1.3 消息服务的非目标

```text
不做通用 MQ
不做多消费组 fan-out
不做全局顺序
不做跨业务线事务
不做消费端 RPC 调用链
```

## 2. 模块地图

### 2.1.1 生产端模块

```text
crm-tx-outbox
  api
    BusinessEvent
    OutboxPublisher
  config
    BizCodeConfig
    BizCodeRegistry
  persistence
    LocalMessage
    LocalMessageMapper
    MybatisOutboxPublisher
  producer
    OutboxScanner
    OutboxBuffer
    OutboxSender
    OutboxProducer
    SenderAssignment
  pipeline
    interceptor
    route
    serializer
    stream
```

### 2.1.2 消费端模块

```text
crm-tx-consumer
  pull
    StreamPuller
    BoundedStreamQueue
    RedisStreamSource
  persist
    ConsumePersister
  worker
    ConsumerWorker
    HandlerRegistry
    MessageHandler
  ack
    AckBatcher
    RedisStreamAcknowledger
  lease
    LeaseManager
    NettyLeaseWheel
    RepositoryLeaseRenewer
  recovery
    PendingRecovery
  persistence
    t_consume_record
    t_idempotent
```

### 2.1.3 Starter 模块

```text
crm-tx-spring-boot-starter
  OutboxProducer 自动装配
  ConsumerBootstrap 自动装配
  BizCode/Stream/Redis 配置
```

## 3. 架构总览

### 3.1.1 生产链路

```text
业务服务
  |
  | 同一个本地事务
  v
+-------------------+       +----------------------+
| 业务表            |       | t_local_message      |
| quota/coupon      |       | status=INIT          |
+---------+---------+       +----------+-----------+
          |                            |
          +-------------+--------------+
                        |
                     COMMIT
                        |
                        v
              OutboxScanner（扫 INIT）
                        |
                        v
              OutboxBuffer（有界队列）
                        |
                        v
              OutboxSender（批量发送）
                        |
                        v
              Redis Stream tx:stream:{bizCode}
                        |
                        v
              t_local_message.status=SENT
```

### 3.1.2 消费链路

```text
Redis Stream
  |
  | XREADGROUP
  v
StreamPuller
  |
  v
BoundedStreamQueue
  |
  v
ConsumePersister
  | 批量 INSERT t_consume_record(READY)
  v
ConsumerWorker
  | claim READY -> PROCESSING
  | 业务表 + t_consume_record(CONSUMED) + t_idempotent
  v
AckBatcher
  | 批量 XACK
  v
PEL 删除
```

### 3.1.3 存储边界

```text
MySQL：
  消息正本、消费记录、幂等、租约、重试状态

Redis Stream：
  消息传输、消费组游标、PEL
  （当前仓库不用 Redis 做幂等占位）

业务库：
  业务事实
```

### 3.1.4 为什么一条业务线一个 Stream？

- 核心结论：隔离业务线，避免一个业务线的积压/故障影响其他线。
- 核心原理
  - Stream key 由 bizCode 决定。
  - 消费组也按业务线隔离。
- 关键机制
  - `tx:stream:lead`、`tx:stream:quota`、`tx:stream:order`。
  - 不做多下游 fan-out。
- 面试要点
  - 这是“业务线隔离”，不是“消息广播”。

## 4. Outbox 生产端

### 4.1.1 BusinessEvent 有哪些字段？

```java
public record BusinessEvent(
        String eventKey,
        String bizCode,
        String bizId,
        String eventType,
        String payload) {
}
```

含义：

```text
eventKey   业务事件唯一键，消费幂等优先用它
bizCode    业务线，决定 Stream key
bizId      业务单号，分片/定位
eventType  事件类型，决定 handler
payload    事件正文
```

### 4.1.2 OutboxPublisher 为什么只写 MySQL？

- 核心结论：写入必须和业务表在同一个本地事务里。
- 核心原理
  - 写 Redis 不能加入 MySQL 事务。
  - 先写 MySQL，失败可重扫。
- 关键机制
  - `publish` 返回 msgId。
  - `t_local_message.status=INIT`。
  - 不直接调用 Redis。
- 面试要点
  - 业务方只需要依赖 `OutboxPublisher.publish`。

### 4.1.3 业务事务没提交，消息会怎样？

```text
业务表 INSERT/UPDATE
+ t_local_message INSERT
同一个事务
```

如果事务回滚：

```text
业务表和 t_local_message 一起回滚
消息不存在
```

### 4.1.4 BizCodeRegistry 做什么？

- 核心结论：生产端白名单 + Stream key 推导。
- 核心原理
  - 只允许注册过的 bizCode 发布。
  - 未知 bizCode 直接抛异常。
- 关键机制
  - `streamKey(prefix, bizCode) = prefix + bizCode`。
  - 拒绝重复 bizCode。
- 面试要点
  - 防止业务方拼错 bizCode 写错 Stream。

### 4.1.5 消息表状态怎么流转？

```text
INIT
  -> SENT
```

说明：

```text
INIT：业务事务已提交，等待 relay
SENT：已经 XADD 到 Redis Stream
```

失败：

```text
发送失败 -> 保持 INIT -> Scanner 重扫
```

### 4.1.6 OutboxScanner 怎么扫？

```text
按 status=INIT
按 created_at/next_retry_at 排序
批量领取
```

关键点：

```text
不能全表扫
必须有索引
多实例要避免重复领取
```

### 4.1.7 OutboxBuffer 为什么存在？

- 核心结论：把“扫描”和“发送”解耦，削峰填谷。
- 核心原理
  - Scanner 只负责找消息。
  - Buffer 有界，防止内存无限增长。
- 关键机制
  - 队列满时反压 Scanner。
  - 批量交给 Sender。
- 面试要点
  - 不是所有环节都直接访问 Redis。

### 4.1.8 OutboxSender 怎么发？

```text
取一批 OutboxRecord
按 bizCode 分组
按 Stream 批量 XADD
更新 t_local_message.status=SENT
记录 stream_entry_id
```

注意：

```text
XADD 成功、状态回写失败
  -> 消息可能重复发送
  -> 消费端幂等兜底
```

### 4.1.9 发送失败怎么办？

```text
Redis 不可用
  -> 本批不标记 SENT
  -> 退避后重扫
  -> 消息仍在 MySQL
```

不会：

```text
把消息从 MySQL 删除
只发 Redis 不留正本
```

### 4.1.10 为什么需要 SenderAssignment？

- 核心结论：多实例下按 bizCode/Stream 分配发送责任，减少竞争。
- 核心原理
  - 一个 Stream 尽量由一个 sender 负责。
  - 避免多实例重复抢同一批。
- 关键机制
  - 分配表/内存映射。
  - 失败后重新分配。
- 面试要点
  - 不是所有实例都扫所有业务线。

## 5. Redis Stream 核心模型

### 5.1.1 Stream entry 是什么？

```text
XADD tx:stream:lead * msgId m-1 ...
```

返回：

```text
1793967010576-0
```

含义：

```text
Stream 是一个只追加日志
entry ID = 时间戳-序号
消息读取后不会自动删除
```

### 5.1.2 consumer group 是什么？

```text
tx:stream:lead
  └── group tx-group-lead
        ├── consumer-1
        ├── consumer-2
        └── consumer-3
```

作用：

```text
多个消费者竞争同一条消息
组维护 last-delivered-id
组维护 PEL
```

### 5.1.3 PEL 是什么？

```text
Pending Entries List
已投递但未 XACK 的消息
```

作用：

```text
消费者崩溃后，消息还在 PEL
其他消费者可以 XCLAIM；XAUTOCLAIM 是 Redis 提供的高级封装，当前代码用 XPENDING + XCLAIM
```

### 5.1.4 XACK 做了什么？

```text
XACK stream group entryId
  -> 从 PEL 删除
  -> Stream entry 仍然存在
```

注意：

```text
XACK 不是删除消息
只是确认消费组已经处理
```

### 5.1.5 XPENDING/XCLAIM 解决什么？

- 核心结论：扫描 PEL 中空闲太久的消息，转移给活着的消费者。
- 核心原理
  - 原消费者崩溃/卡死。
  - 消息不能永远挂在 PEL。
- 关键机制
  - `PendingRecovery` 先用 XPENDING 扫 pending。
  - 已有记录且 lease 过期 -> 标记 READY。
  - 没有消费记录 -> XCLAIM 后插入 READY。
  - XAUTOCLAIM 是 Redis 的高级封装，当前代码没有用它。
- 面试要点
  - 恢复不是“重启消费者”，而是“转移 pending”。

### 5.1.6 为什么不用 Redis List？

| 能力 | List | Stream |
|---|---|---|
| 消费组 | 无 | 有 |
| PEL | 无 | 有 |
| 未 ACK 重投 | 自己做 | 原生 |
| 崩溃恢复 | 弱 | 强 |
| 历史回溯 | 难 | 按 ID |

结论：

```text
本仓库用 Stream
原项目历史文档里的 List 是旧口径
```

## 6. 消费端流水线

### 6.1.1 Puller 怎么拉？

```text
XREADGROUP GROUP tx-group-lead consumer-1
  COUNT 500
  BLOCK 1000
  STREAMS tx:stream:lead '>'
```

拉取后：

```text
消息进入 consumer-1 的 PEL
last-delivered-id 前进
尚未 XACK
```

### 6.1.2 为什么先放 BoundedStreamQueue？

- 核心结论：拉取和落库解耦，控制内存。
- 核心原理
  - Redis 拉取速度快，DB 落库慢。
  - 有界队列防止 OOM。
- 关键机制
  - 队列满反压 Puller。
  - Persister 批量消费。
- 面试要点
  - 不能无限把消息堆在 JVM 内存。

### 6.1.3 Persister 做什么？

```text
takeBatch
-> 批量 INSERT t_consume_record(status=READY)
-> 不 XACK
```

为什么不 XACK：

```text
XACK 必须等业务真正处理成功
否则崩溃就丢消息
```

### 6.1.4 Worker 怎么处理？

```text
claim READY -> PROCESSING
  owner_token = UUID
  lease_until = now + lease
  owner_epoch = N

执行业务 handler

业务表更新
+ t_consume_record(CONSUMED)
+ t_idempotent 插入
COMMIT

提交 ACK
```

### 6.1.5 为什么不能先 XACK 再处理？

```text
XACK 后 PEL 删除
消费者崩溃
消息无法恢复
```

正确顺序：

```text
业务事务提交成功 -> XACK
```

### 6.1.6 XACK 失败怎么办？

```text
业务已经 CONSUMED
PEL 还在
其他消费者重新领取
  -> 幂等检查发现 CONSUMED
  -> 跳过业务
  -> 补 XACK
```

### 6.1.7 AckBatcher 为什么批量？

```text
每 100ms 或 200 条
按 Stream 分组
一次 XACK entryId...
```

好处：

```text
减少 Redis RTT
提高吞吐
```

## 7. 租约、时间轮与 Fencing

### 7.1.1 为什么需要租约？

```text
worker-1 领取 m-1
worker-1 卡住 60s
lease 过期
worker-2 领取 m-1
worker-1 又恢复
```

如果没有 fencing：

```text
两个 worker 同时执行业务
```

### 7.1.2 租约字段

```text
status=PROCESSING
owner_token=UUID
owner_epoch=N
lease_until
max_deadline
```

### 7.1.3 续约怎么做？

```text
LeaseRenewer 后台扫描 in-flight
批量 UPDATE lease_until
WHERE msg_id IN (...)
  AND status='PROCESSING'
  AND owner_token=?
  AND owner_epoch=?
```

不能：

```text
每条消息一个 Timer
每条消息一个 UPDATE
```

### 7.1.4 时间轮解决什么？

```text
10W QPS 不能每任务一个线程/定时器
用分桶时间轮批量调度续约/超时
```

参数示例：

```text
tick=1s
wheelSize=512
lease=60s
renewAfter=20s
maxProcessing=5min
```

### 7.1.5 Fencing 怎么阻止旧 worker 提交？

```sql
UPDATE t_consume_record
SET status='CONSUMED'
WHERE msg_id=?
  AND status='PROCESSING'
  AND owner_token=?
  AND owner_epoch=?
  AND lease_until > NOW()
```

影响行数 0：

```text
任务已被接管
旧 worker 不能提交
```

### 7.1.6 Worker 卡死不响应中断怎么办？

```text
停止续约
标记 LOST
Future.cancel(true)
告警
lease 到期后其他 worker 接管
```

注意：

```text
不能安全 Thread.stop
外部副作用必须幂等
```

## 8. 幂等设计

### 8.1.1 当前仓库的幂等防线是什么？

```text
msgId / eventKey 幂等
MySQL t_idempotent 唯一约束
```

### 8.1.2 为什么不是 Redis 占位？

```text
当前仓库只在 MySQL 做幂等：
  insert ignore t_idempotent
  UNIQUE(msg_id) / UNIQUE(biz_key)
Redis 只做 Stream 传输，不承担幂等正本
```

### 8.1.3 原项目为什么 Redis + MySQL 两边放？

```text
原项目口径：
  Redis 快速挡重复，降低 DB 压力
  MySQL 唯一约束做最终兜底
本仓库没有落地 Redis 占位，面试时要区分两个口径
```

### 8.1.4 幂等键怎么选？

```text
优先 eventKey（业务事件键）
eventKey 为空回退 msgId
```

### 8.1.5 外部副作用怎么办？

```text
发短信/调第三方/写搜索
必须带业务幂等键
或通过 Outbox/补偿，不要裸调
```

## 9. 故障矩阵

| 故障 | 消息在哪 | 谁恢复 | 结果 |
|---|---|---|---|
| 业务事务回滚 | 无消息 | 无 | 不发送 |
| 业务提交后 relay 崩溃 | MySQL INIT | Scanner | 重发 |
| Redis 不可用 | MySQL INIT/SENT | Scanner/Sender | 退避重试 |
| Puller 拉取后崩溃 | PEL | XPENDING/XCLAIM | 重新投递 |
| Persister 前崩溃 | 内存队列丢 | PEL | 重新拉取 |
| Persister 后崩溃 | t_consume_record READY | 本地恢复 | 重新处理 |
| Worker 崩溃 | PROCESSING + lease | 租约过期 | 其他 worker 接管 |
| XACK 失败 | PEL | 重投 + 幂等 | 补 XACK |
| handler 永久失败 | PROCESSING | 重试/DEAD | 人工/告警 |

## 10. 参数与容量

### 10.1.1 关键参数

```yaml
consumer:
  pull:
    count: 500
    block: 1000
  queue:
    capacity: 10000
  lease:
    duration: 60s
    renew-after: 20s
    max-processing: 5min
  ack:
    batch-size: 200
    interval: 100ms
```

### 10.1.2 批量大小怎么定？

```text
太小：RTT 多、吞吐低
太大：单批延迟高、内存压力大
```

本仓库本机压测约 700 msg/s，参数按批量和 DB/Redis 能力调。

### 10.1.3 为什么不做 fan-out？

```text
一个业务线一个消费组
不同下游用不同业务线/Stream
避免“一条消息被多个组重复消费”的复杂度
```

### 10.1.4 积压怎么发现？

```text
XINFO GROUPS 的 lag
PEL pending 数
消费者处理延迟
```

### 10.1.5 本仓库没有做什么？

```text
没有生产 8W QPS
没有 Redis Cluster 分片实测
没有自动扩容
没有管理台重放
```

## 11. 表结构

### 11.1.1 t_local_message

```text
msg_id PK
event_key UK
biz_code / biz_id / event_type
payload / payload_ref
status
retry_count / next_retry_at
stream_entry_id
created_at / updated_at
```

### 11.1.2 t_consume_record

```text
msg_id PK
event_key
biz_code / biz_id / event_type
stream_entry_id
consumer_id
status
owner_token / owner_epoch
claim_count
lease_until
retry_count / next_retry_at
payload / payload_ref
```

索引：

```text
(status, lease_until)
(biz_code, status, next_retry_at, created_at)
(owner_token, status)
(stream_entry_id)
```

### 11.1.3 t_idempotent

```text
msg_id PK
biz_key UK
status
created_at
```

## 12. 设计取舍

### 12.1.1 为什么 MySQL 是正本？

```text
可事务、可恢复、可审计
Redis 是缓存/传输，不是事实源
```

### 12.1.2 为什么 Redis Stream 不是 MQ？

```text
Stream 没有完整 MQ 的消费组管理/死信/事务消息生态
本项目只用它的传输和 PEL
```

### 12.1.3 为什么至少一次而不是 exactly-once？

```text
网络/进程崩溃无法完全消除重复
用幂等把“至少一次”收敛成业务上的“一次”
```

### 12.1.4 为什么消息体不直接放 Redis？

```text
Redis 丢消息不能恢复
MySQL 正本可重放
```

## 13. 面试题（消息专项）

### 13.1.1 消息为什么先写 MySQL？

因为业务表和消息表必须同一个本地事务；写 Redis 无法参与 MySQL 事务。

### 13.1.2 Outbox 的 relay 失败会丢消息吗？

不会。消息还是 INIT/SENT 状态，Scanner/Sender 可以重扫。

### 13.1.3 Stream 和 List 的区别？

Stream 有消费组、PEL、XACK、XPENDING/XCLAIM；List 弹出即丢，恢复弱。

### 13.1.4 消费者崩溃后消息在哪？

在 PEL 里，PendingRecovery 扫 XPENDING，租约过期后标记 READY，缺记录时 XCLAIM。

### 13.1.5 为什么先业务提交再 XACK？

先 XACK 会删除 PEL；业务崩溃后消息无法恢复。业务提交成功后再 XACK 才安全。

### 13.1.6 XACK 失败怎么办？

消息仍在 PEL；重新投递后靠幂等表发现已消费，补 XACK。

### 13.1.7 重复消费怎么挡？

当前仓库用 MySQL `t_idempotent` 唯一约束；原项目口径是 Redis 快速占位 + MySQL 兜底。业务 handler 仍要幂等。

### 13.1.8 为什么有租约还要 fencing？

租约只防并发领取；旧 worker 可能恢复后提交，必须用 owner_token/epoch 让旧提交影响 0 行。

### 13.1.9 时间轮解决什么？

避免每任务一个 Timer/线程；批量续约和超时检查，适合高并发。

### 13.1.10 消费失败重试几次？

配置上限 + 退避；超限 DEAD/失败记录，本仓库没有管理台。

## 14. 一分钟介绍

```text
消息服务是 MySQL Outbox + Redis Stream。
业务在本地事务里写业务表和 t_local_message(INIT)，
Scanner/Buffer/Sender 异步把消息 XADD 到按业务线划分的 Stream，
成功后标记 SENT。
消费端用 consumer group + PEL 拉取，先落 t_consume_record(READY)，
Worker 领取后执行业务，业务表、消费记录、幂等表同事务提交，再批量 XACK。
失败靠 PEL/XPENDING/XCLAIM、租约时间轮、owner fencing 和 MySQL 幂等恢复。
本机压测约 700 msg/s，不宣称生产 8W。
```

## 15. 一句话

> **Outbox 保证“业务成功消息一定在 MySQL”，Stream 保证“消息能高效搬运”，PEL + 租约 + 幂等保证“消费者崩了还能收敛”。**


## 16. 代码级参数与默认值

### 16.1.1 OutboxConfig defaults

```text
streamPrefix       tx:stream:
linger             10ms
batchSize          256
bufferCapacity     50,000 条
bufferCapacityBytes 64MB
scanBatch          1,000
scanInterval       10ms
retryAfter         30s
maxRetries         5
senderThreads      8
perBizCapacity     10,000 条
perBizCapacityBytes 16MB
```

怎么理解：

```text
linger 10ms：牺牲一点延迟换批量
batch 256：单次 XADD 的消息数
buffer 50k/64MB：双重容量限制
scan 1k/10ms：找消息频率
senderThreads 8：按 bizCode 分 shard，shard 数不超过 bizCode 数
```

### 16.1.2 ConsumerRuntimeConfig defaults

```text
queueCapacity        10,000
persistBatchSize     500
persistPollTimeout   100ms
workerBatchSize      200
workerThreads        8
leaseDuration        60s
maxProcessing        5min
retryAfter           30s
maxRetries           3
leaseRenewAfter      20s
ackBatchSize         200
ackQueueCapacity     100,000
ackFlushInterval     100ms
recoveryBatchSize    100
recoveryScanInterval 30s
pullCount            500
pullBlock            1s
```

### 16.1.3 参数怎么定

```text
Pull count 大：吞吐高，单批延迟/内存大
Lease 短：恢复快，但续约压力大
RenewAfter 必须显著小于 Lease
MaxProcessing 是硬上限，防止无限续约
Ack batch 大：Redis RTT 少，但故障时 PEL 停留更久
Retry max 大：更耐故障，但更久占用资源
```

## 17. 生产端代码细节

### 17.1.1 Scanner 为什么按 bizCode 公平扫描？

`OutboxScanner`：

```text
每个业务线每轮至少一个名额
perBizLimit = max(1, ceil(scanBatch / bizCount))
cursor 每轮移动
```

解决：

```text
lead 消息很多时不能把 quota/order 饿死
```

### 17.1.2 inFlight 去重

```text
消息进入 buffer 前先 inFlight.add(msgId)
已经在 buffer/发送中 -> 下一轮跳过
发送结束/失败 finally inFlight.remove
```

没有它：

```text
Scanner 每轮重复把同一条消息放进 buffer
```

### 17.1.3 Buffer 的双重容量

```text
总条数上限
总字节上限
单 bizCode 条数上限
单 bizCode 字节上限
```

背压：

```text
Scanner 用 tryPut
某个业务线满 -> 跳过，不阻塞其他业务线
```

### 17.1.4 Sender 的 linger + batch

```text
先取第一条
在 linger 窗口内继续聚合
最多 batchSize 条
按 Stream 分组
```

为什么：

```text
单条 XADD RTT 太高
批量太大延迟高
linger 是吞吐/延迟的折中
```

### 17.1.5 二分拆分 poison 消息

`publishOrSplit`：

```text
整批 XADD 失败
-> 二分
-> 直到单条
-> 单条失败标记该消息 failed
```

好处：

```text
一条坏消息不会拖死整批好消息
```

### 17.1.6 XADD 成功但状态回写失败

```text
Stream 已写入
t_local_message 仍 INIT
下一轮重投
```

结果：

```text
消息可能重复
消费端幂等兜底
```

这是“至少一次”的必然代价。

### 17.1.7 Sender shard 怎么分？

`SenderAssignment`：

```text
bizCode 排序
轮询分配到 shard
shard 数 = min(senderThreads, bizCodeCount)
```

好处：

```text
不会出现 8 个线程抢 2 个队列
每个 sender 只处理自己的 bizCode
```

## 18. 消费端代码细节

### 18.1.1 XPENDING 恢复流程

`PendingRecovery.recoverOnce`：

```text
XPENDING stream group - + batchSize
for each pending:
  findByStreamEntryId
  CONSUMED/DEAD -> XACK
  READY -> 跳过
  PROCESSING 且 lease 未过期 -> 跳过
  PROCESSING 且 lease 过期 -> markExpiredProcessingReady
  没有记录 -> XCLAIM -> insertReadyBatch
```

注意：

```text
当前代码不用 XAUTOCLAIM
XPENDING + XCLAIM 更可控
```

### 18.1.2 Worker claim 死锁重试

`claimReadyBatch`：

```text
DeadlockLoserDataAccessException
-> 最多 3 次
-> 2ms/4ms 退避
```

为什么：

```text
多 worker 同时 claim 同一业务线
数据库可能选错锁顺序
```

### 18.1.3 Worker 处理事务

```text
transactions.executeWithoutResult:
  idempotent.tryAcquire(msgId, idempotencyKey)
  if firstDelivery:
     handler.handle
  repository.markConsumed(ownerToken, ownerEpoch)
  updated != 1 -> LeaseLostException
```

关键：

```text
幂等插入、业务 handler、消费记录更新在同一个事务
```

### 18.1.4 AckBatcher 队列满的风险

```java
boolean accepted = queue.offer(command);
if (!accepted) {
    log.warn("ack queue full, drop ack command...");
}
```

含义：

```text
ACK 命令丢弃
消息仍在 PEL
PendingRecovery 会再处理/补 ACK
```

代价：

```text
PEL 停留、重复投递、延迟上升
```

生产上应监控 ack 队列满日志。

### 18.1.5 Worker 失败怎么重试？

```text
handler 抛异常
-> markRetry(nextRetryAt=now+retryAfter, maxRetries)
-> retryCount+1
-> 超限 markDead
```

当前默认：

```text
retryAfter=30s
maxRetries=3
```

### 18.1.6 MessageHandlingException / LeaseLostException

```text
MessageHandlingException
  业务 handler 失败，重试

LeaseLostException
  ownerToken/ownerEpoch 不匹配，任务被接管
  旧 worker 不提交，不 ACK
```

### 18.1.7 为什么 ACK 不放在业务事务里？

```text
XACK 是 Redis 操作
不能加入 MySQL 本地事务
所以先提交 MySQL，再批量 ACK
```

## 19. 面试追问

### 19.1.1 Outbox 能不能保证不重复？

不能。至少一次投递必然可能重复；业务幂等负责收敛。

### 19.1.2 为什么 XADD 成功但 DB 状态回写失败时不删消息？

因为删了就无法重投；保留 INIT，让下一轮重复投递，用幂等兜底。

### 19.1.3 为什么 Scanner 不直接发送？

扫描和发送解耦：

```text
扫描慢不拖发送
发送慢不阻塞扫描
buffer 削峰
```

### 19.1.4 为什么 Sender 要按 bizCode 分 shard？

避免多线程争抢同一个队列，同时保证单业务线内批量局部性。

### 19.1.5 消费者为什么不直接处理 PEL？

Puller 只负责拉取/入队；Persister 落库；Worker 处理；职责分开，故障可定位。

### 19.1.6 租约续约失败会怎样？

```text
任务从 in-flight 移除
lease 到期后其他 worker 接管
旧 worker 的 markConsumed 因 ownerToken/epoch 不匹配失败
```

### 19.1.7 消费端能不能做到 exactly-once？

不能对任意外部副作用保证；数据库内靠事务+幂等表可以做到业务效果一次。

### 19.1.8 消息服务最大的风险是什么？

```text
ACK 队列满/丢失 -> PEL 滞留
Scanner/Sender 单点/背压
Redis 故障时的降级
消费 handler 外部副作用不幂等
```

## 20. 一句话总结

> **生产端用 MySQL 正本 + Outbox relay 解决“不丢”，消费端用 Stream/PEL + 租约 + 幂等解决“可恢复、可重放、重复可控”。**
