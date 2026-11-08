# 分布式事务与 TCC 理论面试思维导图

> 范围：分布式事务问题、一致性模型、2PC/3PC/XA、TCC、SAGA、AT、本地消息/事务消息、Outbox、幂等、补偿、选型。
> 用法：标题层级即思维导图节点；项目落地映射见 `58CRM-TCC.md` 和 `58CRM-消息服务.md`。
> 原则：先讲问题和取舍，再讲协议；不把某个框架的实现当成唯一答案。

## 0. 先建立坐标系

### 0.1.1 分布式事务到底在解决什么？

- 核心结论：一次业务操作要跨多个数据库/服务，但每个本地事务只能保证自己的库。
- 核心原理
  - 服务 A 提交成功，服务 B 失败，A 的提交无法被本地回滚。
  - 网络超时导致“结果未知”，不能简单当成功或失败。
- 关键机制
  - 需要全局协调、资源预留/补偿、状态持久化、重试恢复。
- 面试要点
  - 不是“引入一个 MQ”就叫分布式事务。
  - 核心是“跨资源的状态最终如何收敛”。

### 0.1.2 先分清三个问题

```text
原子性：多个资源要么都成功，要么都撤销
一致性：业务约束最终是否成立
隔离性：中间态是否会被其他事务看到
```

很多方案只解决其中一个：

```text
本地消息表：解决“业务和消息”的原子性，不解决资源预留
TCC：解决资源预留/确认/取消，但隔离性弱
Saga：解决长事务补偿，但不保证隔离
2PC：强协调，但阻塞和性能代价高
```

### 0.1.3 一致性模型

```text
强一致：读到的都是最新已提交
线性一致：所有操作像在单点上按实时顺序执行
可串行化：并发结果等价于某个串行顺序
最终一致：短期允许中间态，最终收敛
```

面试要点：

```text
CAP 不是“三选二”口号
分区发生时，强一致和可用性需要取舍
BASE 是基本可用 + 软状态 + 最终一致
```

### 0.1.4 分布式事务的失败模型

```text
节点宕机
网络超时/丢包/重复
消息重复
时钟漂移
协调者宕机
参与者宕机
分区恢复后旧请求到达
```

核心难点不是“正常流程”，而是：

```text
结果未知时怎么办
重复请求怎么办
旧请求晚到怎么办
协调者崩了谁接管
```

## 1. 2PC / 3PC / XA

### 1.1.1 2PC 的两个阶段

```text
阶段一 Prepare：
  协调者询问所有参与者：能否提交？
  参与者执行本地事务但不提交，锁住资源，回复 Yes/No

阶段二 Commit/Rollback：
  全部 Yes -> 协调者发 Commit
  任一 No  -> 协调者发 Rollback
```

成功条件：

```text
所有参与者 Prepare Yes
```

### 1.1.2 2PC 的问题

- 同步阻塞
  - Prepare 后参与者持有本地锁，等待协调者决定。
- 单点故障
  - 协调者宕机，参与者可能一直阻塞。
- 数据不一致
  - 部分参与者收到 Commit，部分没收到。
- 性能差
  - 两次网络往返 + 本地锁持有时间长。

### 1.1.3 3PC 改进了什么？

```text
CanCommit -> PreCommit -> DoCommit
```

目的：

```text
减少参与者长期阻塞
在 PreCommit 后，参与者知道“大家大概率能提交”
```

但没有根治：

```text
网络分区下仍可能出现不一致
协调者/参与者失败仍有边界
工程复杂度更高
```

### 1.1.4 XA 是什么？

- 核心结论：XA 是数据库/资源管理器层面的两阶段提交规范。
- 核心原理
  - TM 协调多个 XA Resource。
  - Prepare/Commit/Rollback 由数据库驱动参与。
- 关键机制
  - 强一致、锁粒度大、性能差。
  - 适合短事务、低并发、强一致场景。
- 面试要点
  - XA 不是业务 TCC；它锁数据库资源。
  - 微服务高并发下通常不优先选 XA。

### 1.1.5 2PC 一定不能用吗？

不是。适用边界：

```text
参与者少
事务短
并发不高
强一致要求高
能接受阻塞和性能代价
```

不适用：

```text
长事务
高并发
跨互联网
参与者不可控
```

## 2. TCC 模型

### 2.1.1 TCC 三个角色

```text
TM：Transaction Manager，发起全局事务
TC：Transaction Coordinator，维护全局/分支状态，调度 Confirm/Cancel
RM：Resource Manager，实现 Try/Confirm/Cancel
```

注意：

```text
TM 和 TC 可以是同一个进程，也可以是独立服务
RM 是业务服务
TC 不应该执行业务逻辑
```

### 2.1.2 TCC 三个阶段

```text
Try：检查并预留资源
Confirm：把预留转正式
Cancel：释放预留
```

例：下单扣库存

```text
Try：库存 available - 1，frozen + 1
Confirm：frozen - 1，sold + 1
Cancel：frozen - 1，available + 1
```

例：优惠券

```text
Try：AVAILABLE -> LOCKED
Confirm：LOCKED -> USED
Cancel：LOCKED -> AVAILABLE
```

### 2.1.3 Try 的职责

- 核心结论：Try 只做“检查 + 预留”，不写最终业务结果。
- 核心原理
  - Try 成功不代表业务一定成功。
  - Confirm 才写终态。
- 关键机制
  - 预留资源要有明确状态（frozen/locked/reserved）。
  - Try 必须幂等。
- 面试要点
  - Try 里直接扣减终态，是常见错误。

### 2.1.4 Confirm 的职责

- 核心结论：Confirm 把预留转成正式结果，必须幂等。
- 核心原理
  - TC 可能重复回调。
  - Confirm 失败要能重试。
- 关键机制
  - 业务终态 + barrier + Outbox 同本地事务。
  - Confirm 不应依赖“Try 时的临时判断”。
- 面试要点
  - Confirm 不是“再执行一次 Try”。

### 2.1.5 Cancel 的职责

- 核心结论：Cancel 释放 Try 预占的资源，必须幂等且能处理空回滚。
- 核心原理
  - Try 可能没到、可能失败、可能超时。
  - Cancel 可能重复。
- 关键机制
  - 无 barrier -> SUSPENDED，不执行释放。
  - 已 CANCELLED/SUSPENDED -> 直接成功。
- 面试要点
  - Cancel 不能假设 Try 一定成功。

### 2.1.6 TCC 与 2PC 的本质区别

| 维度 | 2PC/XA | TCC |
|---|---|---|
| 资源锁 | 数据库锁 | 业务预留状态 |
| 锁持有时间 | 到二阶段结束 | Try 后即可释放数据库锁 |
| 隔离性 | 较强 | 弱，需要业务隔离 |
| 业务侵入 | 低 | 高，要写三个方法 |
| 性能 | 差 | 较好，但 RPC 多 |
| 补偿 | 数据库回滚 | 业务 Cancel |
| 适用 | 短事务强一致 | 长事务、跨服务、资源预留 |

### 2.1.7 TCC 的优点

```text
不长期占用数据库锁
资源提前预留，避免超卖
适合跨服务、长事务
Confirm/Cancel 可重试、可恢复
```

### 2.1.8 TCC 的代价

```text
业务要写三个方法
Try/Confirm/Cancel 都要幂等
隔离性弱，可能出现脏读
Cancel 可能失败，需要重试/人工
框架和运维复杂度高
```

## 2.5 TCC 工程化细节

### 2.5.1 global 状态机

```text
TRYING
  -> CONFIRMING -> CONFIRMED
  -> CANCELLING -> CANCELLED
  -> FAILED（不可自动恢复）
```

原则：

```text
状态只能向前
更新带 WHERE status=?
影响行数为 0 表示并发推进，不重复执行
```

### 2.5.2 branch 状态机

```text
TRYING
  -> TRY_SUCCESS -> CONFIRMING -> CONFIRMED
  -> TRY_FAIL
  -> CANCELLING -> CANCELLED
  -> DEAD
```

注意：

```text
branch 的 TRY_FAIL 不代表 Cancel 完成
global 还要把所有已成功分支 Cance 掉
```

### 2.5.3 TCC 事务日志字段

```text
global：
  xid / request_id / biz_code / biz_id
  status / timeout_at / branch_seq
  owner_token / lease_until

branch：
  xid + branch_id
  resource_key / action_name / callback_endpoint
  status / retry_count / next_retry_at
  application_data

barrier（RM 本地）：
  xid + branch_id
  action_name / status
```

关键：

```text
global/branch 是 TC 的正本
barrier 是 RM 的正本
不能只放内存/Redis
```

### 2.5.4 分支注册职责

目标：

```text
RM 在 Try 入口 registerBranch
TM 只传 xid + payload
TC 从 branch 行读 callback endpoint
```

为什么：

```text
actionName/resourceKey/callbackEndpoint 属于 RM
TM 不该知道 RM 部署细节
```

反模式：

```text
TM 替 RM 注册
TM 配置 RM 地址
TC 去 Nacos 发现 RM
```

### 2.5.5 TCC 超时体系

```text
Try 超时：预留窗口
Confirm/Cancel 重试：退避 + 上限
全局事务超时：最终收敛
gRPC deadline：单次调用超时
```

区分：

```text
网络超时 != 业务失败
超时后结果未知，不能假设 Try 没发生
```

### 2.5.6 TCC 重试策略

```text
可重试：网络、RM 暂时不可用、RETRYABLE
不可重试：业务永久失败、参数错误、状态冲突
```

存储：

```text
next_retry_at + retry_count
进程重启后仍能恢复
```

上限：

```text
DEAD/FAILED + ERROR 日志 + 人工处理
```

### 2.5.7 Coordinator 高可用

```text
TC 无状态
状态在 MySQL
owner_token + lease_until
Scheduler 抢租约推进
旧 owner 的写被 fencing
```

重复回调：

```text
Confirm/Cancel 幂等
barrier 状态判断
业务更新带唯一约束
```

### 2.5.8 TCC 性能成本

```text
Try：一次业务 RPC + barrier + 业务更新
Confirm/Cancel：TC -> RM RPC + barrier + 业务更新
额外 DB：global/branch/barrier
额外网络：注册/上报/回调
```

优化：

```text
批量/异步回调
连接池
状态机 CAS 减少锁
幂等表索引
避免长事务
```

但不能为了性能：

```text
跳过 barrier
跳过持久化
Confirm 里裸发 MQ
```

### 2.5.9 TCC 常见坑

```text
Try 直接写终态
Confirm 再做业务校验导致永久失败
Cancel 不幂等
空回滚直接报错
悬挂没有 SUSPENDED 标记
resourceKey 不唯一
requestId 每次重试都变
callback 无鉴权
没有对账/人工兜底
```

### 2.5.10 TCC 与补偿

```text
TCC 的 Cancel 是业务补偿，不是数据库物理回滚
补偿要满足业务不变量
补偿可能失败，需要重试/人工
补偿不是“删除数据”这么简单
```

## 3. TCC 三防：空回滚、悬挂、幂等

### 3.1.1 空回滚

定义：

```text
Cancel 先到，Try 还没到
```

处理：

```text
没有 barrier -> 插入 SUSPENDED -> 返回成功
不执行 cancelAction
```

原因：

```text
没有 Try 就没有资源
Cancel 的目标已经满足
```

### 3.1.2 悬挂

定义：

```text
Cancel 已经处理完，Try 才迟到
```

处理：

```text
Try 看到 SUSPENDED/CANCELLED -> 返回 FAILURE
不执行业务 Try
```

没有它：

```text
Cancel 释放资源后，迟到 Try 又占资源
事务已结束，资源泄漏
```

### 3.1.3 幂等

```text
Try 重复：已 TRIED/CONFIRMED -> SUCCESS
Confirm 重复：已 CONFIRMED -> SUCCESS
Cancel 重复：已 CANCELLED/SUSPENDED -> SUCCESS
```

冲突：

```text
Confirm 遇到 CANCELLED/SUSPENDED -> 报错
Cancel 遇到 CONFIRMED -> 报错
```

### 3.1.4 三防的存储

```text
barrier 表：(xid, branchId) 主键
status：TRIED/CONFIRMED/CANCELLED/SUSPENDED
```

关键：

```text
barrier 是 RM 本地表
和业务动作在同一个本地事务
```

### 3.1.5 为什么需要事务日志？

```text
记录 Try/Confirm/Cancel 状态
崩溃后能恢复
重试有依据
避免只靠内存
```

## 4. TCC 隔离性与并发

### 4.1.1 TCC 能保证隔离性吗？

- 核心结论：TCC 本身不自动保证强隔离，只保证最终一致。
- 核心原理
  - Try 后资源处于中间态（frozen/locked）。
  - 其他事务可能读到中间态。
- 关键机制
  - 业务状态机、预留字段、版本号、读隔离。
- 面试要点
  - “TCC 没有脏读”是错误说法。

### 4.1.2 常见隔离问题

```text
脏读：读到 Try 的中间态
不可重复读：Try/Confirm 之间状态变化
超卖：两个 Try 同时检查同一库存
```

### 4.1.3 怎么缓解？

```text
Try 用原子 UPDATE 条件扣减（available >= n）
预留状态对外不可见/单独字段
业务查询只读终态
关键资源加行锁/版本号
Confirm/Cancel 用状态机幂等
```

### 4.1.4 Try 的原子性

错误：

```text
SELECT available
if available >= n:
    UPDATE available = available - n
```

并发下可能超卖。

正确：

```sql
UPDATE stock
SET available = available - n,
    frozen = frozen + n
WHERE sku_id = ?
  AND available >= n
```

影响行数为 0 -> Try 失败。

### 4.1.5 Confirm/Cancel 并发

```text
Confirm 和 Cancel 不能同时成功
用 barrier 状态 CAS
```

例如：

```sql
UPDATE barrier
SET status='CONFIRMED'
WHERE xid=? AND branch_id=? AND status='TRIED'
```

## 5. TCC 可靠性设计

### 5.1.1 为什么 Confirm/Cancel 要重试？

```text
网络超时
RM 暂时不可用
RM 返回 RETRYABLE
TC 崩溃恢复
```

原则：

```text
可重试错误 -> 退避重试
永久业务错误 -> DEAD/人工
```

### 5.1.2 重试会不会重复执行业务？

会。所以：

```text
Confirm/Cancel 必须幂等
barrier 状态先判断
业务更新带唯一约束/状态条件
```

### 5.1.3 TC 崩溃怎么办？

```text
TC 无状态 + 状态在 MySQL
其他 TC 抢 owner lease
从 global/branch 状态恢复
```

关键：

```text
owner_token + lease_until
更新带 owner fencing
```

### 5.1.4 为什么 TC 不直接执行业务？

```text
TC 不知道业务语义
TC 只认 xid/branch/action
业务在 RM 执行
```

### 5.1.5 分支注册应该谁做？

目标设计：

```text
RM 在 Try 入口自己注册
TM 只传 xid + payload
```

原因：

```text
actionName/resourceKey/callbackEndpoint 属于 RM
TM 不应该知道 RM 部署细节
```

### 5.1.6 callback 安全

```text
内网可信不是长期方案
需要 token/mTLS/allowlist/重放防护
```

### 5.1.7 超时怎么设计？

```text
Try 超时：业务预留时间
Confirm/Cancel 重试超时：退避+上限
全局事务超时：最终收敛
时钟：不要依赖单机时钟做唯一判断
```

## 6. 其他分布式事务方案

### 6.1.1 SAGA

```text
每个服务执行本地事务
失败时执行补偿事务
```

两种恢复：

```text
向后恢复：反向补偿
向前恢复：重试直到成功
```

优点：

```text
长事务、服务自治、无长期锁
```

缺点：

```text
没有隔离性
补偿可能失败
补偿不是物理回滚
```

### 6.1.2 AT 模式

```text
业务 SQL + 自动反向 SQL
全局锁/本地锁
```

优点：

```text
业务侵入低
```

缺点：

```text
反向 SQL 正确性依赖约束
复杂业务/多表更新难
全局锁有性能代价
```

### 6.1.3 本地消息表 / Outbox

```text
业务表 + 消息表同一个本地事务
后台 relay 投递
消费端幂等
```

解决：

```text
业务成功消息一定在
消息失败可重试
```

不解决：

```text
资源预留
跨服务回滚
消费端 exactly-once
```

### 6.1.4 事务消息

```text
半消息 -> 本地事务 -> Commit/Rollback
Broker 回查
```

解决：

```text
生产者本地事务和消息发送的一致性
```

不解决：

```text
消费者业务失败的回滚
跨多个消费者的原子性
```

### 6.1.5 最大努力通知

```text
业务执行后尽力通知
失败重试有限次
对账兜底
```

适合：

```text
通知、对账、非核心链路
```

不适合：

```text
强一致资源扣减
```

### 6.1.6 exactly-once 存在吗？

工程上通常：

```text
至少一次投递 + 幂等消费 = 业务效果一次
```

真正的端到端 exactly-once 很难，因为：

```text
网络确认不可靠
外部副作用不可回滚
消费者可能崩溃
```

## 7. 选型矩阵

### 7.1.1 按业务选

| 场景 | 优先方案 |
|---|---|
| 短事务、强一致、参与者少 | 2PC/XA |
| 跨服务资源预留、可补偿 | TCC |
| 长流程、服务自治 | SAGA |
| 业务和消息原子 | Outbox/事务消息 |
| 只通知、可延迟 | 最大努力通知 |
| 复杂 SQL 自动回滚 | AT（评估约束） |

### 7.1.2 按代价选

```text
XA：数据库锁、阻塞、性能差
TCC：业务侵入、三方法、幂等、隔离弱
SAGA：补偿逻辑、无隔离
Outbox：最终一致、消费幂等
事务消息：依赖 MQ、回查
```

### 7.1.3 面试回答模板

```text
先说业务一致性要求
再说失败模型和补偿能力
再比较 2PC/TCC/SAGA/Outbox
最后说为什么选这个、代价是什么
```

不要：

```text
一上来背 TCC 三个方法
说“TCC 保证强一致”
说“MQ 保证 exactly-once”
```

## 8. TCC 设计检查清单

### 8.1.1 业务建模

```text
资源是否有可预留状态？
Try 是否能原子扣减/锁定？
Confirm 是否只做终态转换？
Cancel 是否能释放？
补偿是否可重复？
```

### 8.1.2 幂等

```text
requestId 是否稳定？
resourceKey 是否唯一？
Try/Confirm/Cancel 是否幂等？
消费端是否幂等？
```

### 8.1.3 异常

```text
空回滚？
悬挂？
重复回调？
超时？
TC 崩溃？
RM 崩溃？
callback 不可达？
```

### 8.1.4 数据

```text
global/branch/barrier 表
状态机 CAS
owner lease
重试字段
审计日志
```

### 8.1.5 运维

```text
指标：成功率、重试、DEAD、延迟
告警：DEAD/FAILED/积压
人工：查询、重放、补偿
安全：callback 鉴权
```

## 9. 高频面试题

### 9.1.1 TCC 和 2PC 的区别？

2PC 是数据库资源锁，TCC 是业务资源预留；TCC 锁持有短、业务侵入高、隔离弱。

### 9.1.2 TCC 能保证强一致吗？

不能，TCC 是最终一致；中间态可能被读到，需要业务隔离。

### 9.1.3 Try 失败怎么办？

上报失败，TM rollback，TC Cancel 已成功分支。

### 9.1.4 Confirm 失败怎么办？

可重试错误退避重试；永久失败 DEAD/人工；barrier 保证幂等。

### 9.1.5 Cancel 失败怎么办？

重试；空回滚插 SUSPENDED；永久失败人工。

### 9.1.6 空回滚和悬挂的区别？

空回滚是 Cancel 先到；悬挂是 Cancel 后 Try 迟到。两者都用 SUSPENDED 防。

### 9.1.7 为什么 TCC 需要事务日志？

崩溃恢复、幂等、重试、审计；不能只靠内存。

### 9.1.8 为什么 TC 需要 lease？

多 TC 同时推进同一 xid，需要 owner fencing。

### 9.1.9 为什么 Confirm 要与 Outbox 同事务？

业务终态和消息记录同本地事务，防业务成功消息丢。

### 9.1.10 为什么 TCC 不适合所有场景？

业务侵入、三方法、幂等、补偿、隔离弱、运维复杂。

### 9.1.11 SAGA 和 TCC 怎么选？

需要资源预留/防超卖选 TCC；长流程、服务自治、可补偿选 SAGA。

### 9.1.12 AT 和 TCC 怎么选？

AT 业务侵入低但依赖反向 SQL/全局锁；TCC 控制强但业务改造大。

### 9.1.13 本地消息表和事务消息区别？

本地消息表自己存消息 + relay；事务消息依赖 MQ 半消息/回查。

### 9.1.14 消息重复怎么处理？

幂等键 + 唯一约束 + 状态机；至少一次 + 幂等 = 业务一次。

### 9.1.15 分布式事务能不能做到 exactly-once？

端到端很难；工程上用至少一次 + 幂等。

### 9.1.16 CAP 怎么回答？

分区发生时在一致性和可用性之间取舍；不是简单三选二。

### 9.1.17 为什么 2PC 会阻塞？

Prepare 后参与者持有资源锁，等待协调者决定。

### 9.1.18 3PC 解决 2PC 了吗？

减少阻塞窗口，但没有根治分区下不一致。

### 9.1.19 TCC 的脏读怎么缓解？

预留字段、状态机、读终态、行锁/版本号、业务隔离。

### 9.1.20 什么时候用最大努力通知？

非核心通知、可延迟、可对账；不用于强一致扣减。

## 10. 一句话

> **分布式事务没有免费的一致性：2PC 用锁换一致，TCC 用业务预留和补偿换灵活，SAGA 用补偿换自治，Outbox 用最终一致换可靠通知；选型本质是业务一致性、隔离性、复杂度和性能的取舍。**
