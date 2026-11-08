# 分布式事务与 TCC 面试思维导图（理论 + 58CRM 落地）

> 基线：分布式事务通用理论 + `58crm-transaction` 当前仓库实现。
> 用法：标题层级即思维导图节点，可直接导入支持 Markdown 的思维导图工具。
> 结构：
> - 第一部分：分布式事务与 TCC 理论（第 0-10 节）
> - 第二部分：58CRM 项目落地（第 11-28 节）
> 消息服务单独成图：`58CRM-消息服务.md`。
> 口径：项目流程按目标设计讲：RM 在 Try 入口自己注册分支；当前仓库 TM 注册的过渡实现单独标注。

---

# 第一部分：分布式事务与 TCC 理论

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

---

# 第二部分：58CRM 项目落地

> 基线：`58crm-transaction` 当前仓库实现，Java 17 / Spring Boot 3.2 / MySQL / Redis Stream / Nacos / gRPC。
> 范围：项目背景、模块、架构、正确流程、状态机、Barrier、消息链路、注解 API、设计取舍、面试题。
> 用法：标题层级即思维导图节点，可直接导入支持 Markdown 的思维导图工具。
> 口径声明：
> 1. 本文流程按目标设计讲：**RM 在 Try 入口自己注册分支**，TM 不配置 RM 回调地址。
> 2. 当前仓库 `@TccBranch` 仍是 TM 先注册的过渡实现，差异见第 21 节，不把过渡实现当目标设计。
> 3. 第 22 节的 06 文档 66 问按“原项目口径 / 本仓库口径”分别标注，不混用 8W QPS 等未验证数字。
> 4. 消息服务深入版见 `58CRM-消息服务.md`，本文只保留 TCC 主线和消息边界。

## 11. 30 秒总览

### 11.1.1 这个项目到底是什么？

- 核心结论：一个跨服务一致性框架，核心是 TCC（Try/Confirm/Cancel）+ Outbox 可靠消息 + Barrier。
- 核心原理
  - TCC 解决跨服务资源的预留、确认、释放。
  - Outbox 解决业务事实发生后的可靠通知。
  - Barrier 解决 Try/Confirm/Cancel 的重复、空回滚、悬挂。
- 关键机制
  - 独立 Coordinator 管全局/分支状态。
  - TM 发起全局事务，RM 实现三个本地动作。
  - MySQL 是状态正本，Redis Stream 是消息传送带。
- 面试要点
  - 一句话：不是“消息队列”，而是“事务协调 + 可靠事件”。
  - 能区分 TCC 状态与消息状态。

### 11.1.2 三个角色一句话

```text
TM：发起全局事务，调 Try，决定 commit/rollback
RM：实现 Try/Confirm/Cancel，维护 barrier 和业务状态
TC：Coordinator，维护 global/branch 状态，回调 Confirm/Cancel
```

### 11.1.3 当前可验证事实

```text
mvn clean test：222 tests / 0 failures / 1 skipped
真实 E2E：quota + coupon 两个 RM
  成功：xid=1 CONFIRMED，quota assigned=1，coupon USED
  失败：xid=2 CANCELLED，quota 预占被 Cancel，coupon barrier SUSPENDED
消息组件：本机压测约 700 msg/s
```

### 11.1.4 明确不宣称

```text
没有生产 8W QPS 验证
没有 ShardingSphere 分库分表
没有 Sentinel / Prometheus / Grafana
没有 callback 鉴权 / mTLS / allowlist
没有多 TC 故障注入
```

## 12. 项目背景

### 12.1.1 业务场景是什么？

- 核心结论：CRM 线索分配同时涉及 quota（额度）和 coupon（优惠券）等多个服务。
- 核心原理
  - 线索分配不是单库事务。
  - 一个服务成功、另一个失败时，已经预占的资源必须撤销。
- 关键机制
  - 成功：两个 RM 都 Try 成功，再统一 Confirm。
  - 失败：quota Try 成功、coupon Try 失败，TC Cancel quota。
- 面试要点
  - 不要只说“用了 TCC”，要说出“A 成功、B 失败、撤销 A”的场景。

### 12.1.2 为什么本地事务不够？

- 核心结论：本地事务只能保证一个库，跨服务已经提交的操作它回滚不了。
- 核心原理
  - quota 库提交成功，coupon 库失败，本地 rollback 无法撤销 quota。
  - 普通 MQ 只能尽量送达，不能保证“业务操作 + 消息”原子。
- 关键机制
  - TCC 提供资源预留和反向 Cancel。
  - Outbox 提供业务事实和消息记录的本地原子性。
- 面试要点
  - “本地事务 + MQ”解决不了跨库原子性，也解决不了消息与业务同事务。

### 12.1.3 为什么不用纯 MQ？

- 核心结论：MQ 没有资源预留语义，也没有“撤销已经成功分支”的协议。
- 核心原理
  - MQ 是事后通知，不是事前占位。
  - 消费失败只能重试/补偿，不能阻止超卖。
- 关键机制
  - TCC 先 Try 占资源，Confirm 才落终态。
  - Outbox 只负责终态通知，不负责资源一致性。
- 面试要点
  - TCC 和 MQ 不是替代关系，是分工关系。

### 12.1.4 为什么不用 Seata？

- 核心结论：本项目是自研实现，适合学习和可控性；不宣称 Seata 性能不行。
- 核心原理
  - Seata AT 依赖反向 SQL，复杂业务回滚正确性难验证。
  - Seata TCC 需要引入其 TC/客户端体系。
- 关键机制
  - 自研范围收敛在 Coordinator、注解 API、Participant barrier。
  - 消息组件独立于 TCC。
- 面试要点
  - 说取舍，不说“开源都不行”。
  - 自研代价是可靠性、运维、生态都要自己补。

### 12.1.5 项目目标与非目标

- 目标
  - 跨服务资源最终一致。
  - Try 失败能撤销已成功分支。
  - Confirm/Cancel 可重试、可恢复。
  - 业务只写注解和 TccAction。
- 非目标
  - 不做强一致 2PC。
  - 不做通用 MQ。
  - 不做分库分表路由。
  - 不做管理台/告警平台。

## 13. 模块地图

### 13.1.1 Maven 模块怎么分？

```text
crm-tx-bom                       统一依赖版本
crm-tx-common                    通用模型/错误码
crm-tx-tcc-api                   业务契约、状态、注解
crm-tx-tcc-protocol              proto + gRPC stub + 内部 DTO
crm-tx-tcc-client                TM 客户端、Coordinator gRPC 客户端
crm-tx-tcc-participant           RM SDK、barrier、Try/Confirm/Cancel
crm-tx-tcc-coordinator           TC 独立服务
crm-tx-tcc-id                    XID 号段
crm-tx-tcc-spring-boot-starter   TCC client/participant/注解自动装配
crm-tx-tcc-examples              lead / quota / coupon
crm-tx-outbox                    Outbox 生产/扫描/发送
crm-tx-consumer                  Redis Stream 消费
crm-tx-spring-boot-starter       Outbox/Consumer 自动装配
crm-tx-demo                      消息示例
```

### 13.1.2 模块依赖方向是什么？

```text
api <- protocol <- client
                 <- participant
                 <- coordinator
client/participant/coordinator <- tcc starter
examples -> tcc starter + outbox
```

原则：

```text
api 不依赖 protocol
protocol 依赖 api
client 不暴露 proto 给业务
participant 不依赖 coordinator 内部实现
```

### 13.1.3 为什么把 protocol 单独拆出来？

- 核心结论：gRPC stub 和内部 DTO 不属于业务契约。
- 核心原理
  - 业务方只依赖 `tcc-api`。
  - proto 变更不应污染公共 API。
- 关键机制
  - 低层 DTO 放在 `com.wuba.crm.tx.tcc.protocol.model`。
  - stub 放在 `protocol.grpc`。
- 面试要点
  - 包边界是 public / protocol / internal 三层。

### 13.1.4 Starter 怎么分角色？

```text
crm-tx-spring-boot-starter
  Outbox + Consumer

crm-tx-tcc-spring-boot-starter
  TCC client + participant + annotation AOP

crm.tx.tcc.client.enabled=true        TM 才开
crm.tx.tcc.participant.enabled=true   RM 才开
```

注意：

```text
participant 依赖是 optional
TM-only 应用不会被 MyBatis/DataSource 拖进来
```

## 14. 架构设计

### 14.1.1 逻辑架构图

```text
+--------------------- 业务应用（TM） ---------------------+
| LeadAssignService                                       |
|   @TccTransactional                                     |
|   -> RM Try (OpenFeign)                                 |
+---------------------------+-----------------------------+
                            |
                            | gRPC begin/commit/rollback
                            v
+--------------------- Coordinator（TC） ------------------+
| Begin / Commit / Rollback / Query                       |
| global/branch 状态机 + owner lease + Scheduler          |
+---------------------------+-----------------------------+
                            |
                            | gRPC Confirm/Cancel
                            v
+--------------------- 参与方（RM） -----------------------+
| quota-service / coupon-service                          |
|   TccAction + barrier + 本地业务表 + Outbox             |
+---------------------------+-----------------------------+
                            |
                            | 本地事务
                            v
+--------------------- MySQL -----------------------------+
| t_tcc_global / t_tcc_branch                             |
| t_branch_barrier / t_local_message                      |
+---------------------------------------------------------+
```

### 14.1.2 部署拓扑

```text
Nacos：TC 注册 gRPC 实例；TM 通过服务名发现 TC
MySQL：TC 全局/分支状态；每个 RM 自己的业务库
Redis Stream：Outbox 消息投递
Coordinator：无状态多实例，状态在 MySQL
```

### 14.1.3 传输矩阵

| 调用 | 协议 | 谁发起 |
|---|---|---|
| TM -> TC begin/commit/rollback | gRPC | TM |
| TM -> RM Try | OpenFeign / HTTP | TM |
| RM -> TC registerBranch | gRPC（目标设计） | RM |
| RM -> TC report | gRPC | RM |
| TC -> RM Confirm/Cancel | gRPC callback | TC |

### 14.1.4 为什么 TC 不发现 RM？

- 核心结论：TC 只认分支表里的回调元数据，不依赖 RM 的注册中心。
- 核心原理
  - RM 数量多、版本多、地址变化频繁。
  - TC 主动发现 RM 会把服务端和客户端耦死。
- 关键机制
  - 目标设计由 RM 注册 callback endpoint。
  - TC 从 `t_tcc_branch` 读取并回调。
- 面试要点
  - TC 是协调者，不是服务发现代理。

### 14.1.5 为什么 TM 不直接调 Confirm/Cancel？

- 核心结论：Confirm/Cancel 是 TC 根据全局状态决定的，不是业务方随意触发。
- 核心原理
  - TM 只知道 Try 结果，不知道其他分支。
  - TC 才能判断全局该 commit 还是 rollback。
- 关键机制
  - TM commit/rollback -> TC 推进状态 -> TC 回调 RM。
- 面试要点
  - Try 是业务 API，Confirm/Cancel 是 TCC 协议。

## 15. 正确流程（目标设计）

> 本节按目标设计：RM 自注册。当前仓库的 TM 注册差异见 10.2。

### 15.1.1 成功流程

```text
TM                          TC                         RM1(quota)        RM2(coupon)
 |-- begin ---------------->|                            |                 |
 |<-- xid ------------------|                            |                 |
 |-- Try(xid, payload) --------------------------------->|                 |
 |                          |<-- registerBranch ---------|                 |
 |                          |    (actionName/resourceKey  |                 |
 |                          |     callbackEndpoint)       |                 |
 |                          |--- branchId=1 ------------->|                 |
 |                          |                            |-- barrier TRIED |
 |                          |                            |-- reserve       |
 |                          |<-- report TRY_SUCCESS -----|                 |
 |<-- Try success ---------------------------------------|                 |
 |-- Try(xid, payload) ---------------------------------------------------->|
 |                          |<-- registerBranch ----------------------------|
 |                          |--- branchId=2 ------------------------------->|
 |                          |                            |                 |-- barrier TRIED
 |                          |                            |                 |-- lock coupon
 |                          |<-- report TRY_SUCCESS -----------------------|
 |<-- Try success ---------------------------------------------------------|
 |-- commit --------------->|                            |                 |
 |                          |-- Confirm branch1 --------------------------->|
 |                          |<-- CONFIRMED --------------------------------|
 |                          |-- Confirm branch2 -------------------------------->|
 |                          |<-- CONFIRMED ------------------------------------|
 |<-- CONFIRMED ------------|
```

关键点：

```text
Try 由 TM 通过业务 API 调 RM
registerBranch 由 RM 在 Try 入口做
TC 只接收注册和上报
commit 后 TC 回调 Confirm
```

### 15.1.2 失败流程（quota 成功、coupon 失败）

```text
TM                          TC                         RM1(quota)        RM2(coupon)
 |-- begin ---------------->|                            |                 |
 |-- Try quota ----------------------------------------->|                 |
 |                          |<-- registerBranch ---------|                 |
 |                          |--- branchId=1 ------------->|                 |
 |                          |                            |-- barrier TRIED |
 |                          |                            |-- reserve       |
 |                          |<-- report TRY_SUCCESS -----|                 |
 |-- Try coupon ----------------------------------------------------------->|
 |                          |<-- registerBranch ----------------------------|
 |                          |--- branchId=2 ------------------------------->|
 |                          |                            |                 |-- Try 失败
 |                          |                            |                 |-- barrier 回滚
 |                          |<-- report TRY_FAIL --------------------------|
 |<-- Try 失败 ------------------------------------------------------------|
 |-- rollback ------------->|                            |                 |
 |                          |-- Cancel quota ----------------------------->|
 |                          |                            |-- barrier CANCELLED
 |                          |                            |-- release       |
 |                          |-- Cancel coupon -------------------------------->|
 |                          |                            |                 |-- 空回滚 SUSPENDED
 |<-- CANCELLED ------------|
```

关键点：

```text
quota 已经 Try 成功，必须 Cancel
coupon Try 失败，barrier 回滚，Cancel 空回滚
最终 global CANCELLED
```

### 15.1.3 为什么不是 TM 注册分支？

- 核心结论：分支元数据属于 RM，TM 不该知道 RM 的 actionName/resourceKey/callbackEndpoint。
- 核心原理
  - RM 最清楚自己怎么 Try、怎么 Confirm、回调地址是什么。
  - TM 只应该传 xid 和业务 payload。
- 关键机制
  - RM 在 Try 入口注册分支。
  - TC 从 RM 注册的分支行拿 callback endpoint。
- 面试要点
  - 这是目标设计；当前仓库的 `@TccBranch` 仍是过渡实现。

### 15.1.4 当前仓库与目标设计的差异

```text
当前：
  TM @TccBranch -> TC registerBranch
  TM -> RM Try
  callbackEndpoint 写在 TM 注解

目标：
  RM Try 入口 -> TC registerBranch
  TM 只传 xid + payload
  callbackEndpoint 由 RM 自己配置/注册
```

这条差异是技术债，不是本文目标流程。详见 `docs/07-学习讨论/09-2026-11-08-分支注册职责讨论.md`。

## 16. 状态机、租约与重试

### 16.1.1 global 状态

```text
TRYING
  -> CONFIRMING -> CONFIRMED
  -> CANCELLING -> CANCELLED
  -> FAILED（错误日志）
```

### 16.1.2 branch 状态

```text
TRYING
  -> TRY_SUCCESS -> CONFIRMING -> CONFIRMED
  -> TRY_FAIL
  -> CANCELLING -> CANCELLED
  -> DEAD
```

### 16.1.3 barrier 状态

```text
TRIED -> CONFIRMED
      -> CANCELLED
无记录 + Cancel -> SUSPENDED
```

### 16.1.4 owner lease 解决什么？

- 核心结论：多 TC 实例同时推进同一个 xid 时，只允许一个 owner。
- 核心原理
  - `owner_token + lease_until` 在 global 行上。
  - 抢租约 SQL 带状态和过期条件。
- 关键机制
  - branch 不单独存 owner，所有 branch 跟随 global owner。
  - 旧 owner 的写用 owner_token fencing。
- 面试要点
  - 没有 lease，两个 TC 可能同时 Confirm/Cancel。

### 16.1.5 commit/rollback 为什么要分两段？

```text
DB 事务内：
  更新 global/branch 状态
  设置 next_retry_at
DB 事务外：
  调 RM Confirm/Cancel
```

原因：

```text
RM 调用是远程调用，不能占着数据库事务
失败后靠 branch 状态和 Scheduler 重试
```

### 16.1.6 Scheduler 怎么重试？

```text
扫描：
  CONFIRMING/CANCELLING 且 next_retry_at 到期
抢 owner lease：
  owner_token + lease_until
调 RM：
  成功 -> CONFIRMED/CANCELLED
  可重试 -> 退避
  永久失败 -> DEAD
```

退避：

```text
1s / 5s / 30s / 2min / 10min
```

### 16.1.7 为什么 retryable 不立刻无限重试？

- 核心结论：避免故障时打爆 RM/TC，必须有退避和上限。
- 核心原理
  - 网络抖动、RM 重启需要时间。
  - 无限重试会放大故障。
- 关键机制
  - `next_retry_at` 持久化，进程重启后仍能恢复。
  - 超限 DEAD/FAILED，ERROR 日志。
- 面试要点
  - 重试是状态机的一部分，不是 for 循环。

## 17. 数据模型

### 17.1.1 t_tcc_global

```text
xid PK
request_id UK
biz_code / biz_id
status
timeout_at
branch_seq
owner_token / lease_until
created_at / updated_at
```

### 17.1.2 t_tcc_branch

```text
PK (xid, branch_id)
UK (xid, resource_key)
resource_key / action_name / callback_endpoint
status / retry_count / next_retry_at
application_data
created_at / updated_at
```

### 17.1.3 t_branch_barrier

```text
PK (xid, branch_id)
action_name
status：TRIED / CONFIRMED / CANCELLED / SUSPENDED
created_at / updated_at
```

### 17.1.4 本地消息表

```text
msg_id PK
event_key UK
biz_code / biz_id / event_type
payload
status：INIT / SENT / CONSUMED
retry_count / next_retry_at
stream_entry_id
```

### 17.1.5 XID 怎么生成？

- 核心结论：MySQL 号段模式，`id_segment` 表。
- 核心原理
  - `UPDATE max_id = max_id + step` + 同事务 SELECT。
  - 不依赖 Redis/雪花。
- 关键机制
  - XID 从 1 开始，0 跳过。
  - 独立事务，外层回滚不影响号段。
- 面试要点
  - 号段比单号 UPDATE 少一次往返。
  - 多实例靠数据库行锁。

### 17.1.6 branchId 的作用域

```text
xid=1 -> branchId=1,2
xid=2 -> branchId=1
```

不是冲突：

```text
PK 是 (xid, branch_id)
branchId 只在 xid 内唯一
日志必须打 xid:branchId
```

## 18. Barrier 三防

### 18.1.1 空回滚是什么？

- 核心结论：Cancel 先到，Try 还没来。
- 核心原理
  - 没有 barrier，直接执行 cancelAction 可能误释放。
- 关键机制
  - 插入 `SUSPENDED`，不执行 cancelAction。
  - 返回成功。
- 面试要点
  - 空回滚是“没有资源可释放”，不是失败。

### 18.1.2 悬挂是什么？

- 核心结论：Cancel 已经处理完，Try 才迟到。
- 核心原理
  - 迟到 Try 不能再占资源。
- 关键机制
  - Try 看到 `CANCELLED/SUSPENDED` 返回 FAILURE。
- 面试要点
  - `SUSPENDED` 是防悬挂标记。

### 18.1.3 幂等怎么实现？

```text
Try 已 TRIED/CONFIRMED -> SUCCESS
Confirm 已 CONFIRMED -> SUCCESS
Cancel 已 CANCELLED/SUSPENDED -> SUCCESS
```

冲突状态：

```text
Confirm 遇到 CANCELLED/SUSPENDED -> 报错
Cancel 遇到 CONFIRMED -> 报错
```

### 18.1.4 Confirm 为什么必须和 Outbox 同事务？

```text
业务终态 + barrier 更新 + Outbox 插入
在同一个本地事务
```

否则：

```text
业务成功但消息丢
消息发了但业务回滚
```

### 18.1.5 为什么 Cancel 不能直接删 barrier？

- 核心结论：删了就失去幂等和防悬挂依据。
- 核心原理
  - 重复 Cancel 无法判断。
  - 迟到 Try 无法拒绝。
- 关键机制
  - 保留 `CANCELLED/SUSPENDED`。
- 面试要点
  - barrier 是状态表，不是临时锁表。

## 19. 消息链路

### 19.1.1 消息生产链路

```text
业务本地事务
  -> 业务表更新
  -> t_local_message 插入 INIT
COMMIT
  -> Scanner 扫 INIT
  -> Buffer
  -> Sender XADD Redis Stream
  -> 标记 SENT
```

### 19.1.2 为什么先写 MySQL 不直接写 Redis？

- 核心结论：MySQL 是正本，Redis 是可丢缓冲。
- 核心原理
  - Redis 挂了不能丢消息。
  - 先写本地消息表，业务和消息同事务。
- 关键机制
  - Redis 只做传输，失败可重扫。
- 面试要点
  - “先 MySQL 后 Redis”是可靠消息的核心顺序。

### 19.1.3 消费链路

```text
XREADGROUP
  -> PEL 记录
  -> 幂等检查
  -> 业务 handler
  -> XACK
  -> 消费记录/幂等表
```

### 19.1.4 消费者崩溃怎么恢复？

```text
PEL 里还有未 ACK 消息
  -> XPENDING / XCLAIM 恢复
  -> 重新投递或重试
```

### 19.1.5 为什么不用 Redis List？

- 核心结论：List 没有消费组/PEL/ACK，崩溃恢复弱。
- 核心原理
  - Stream 有 consumer group、pending、ack。
  - List 弹出即丢失。
- 关键机制
  - 本项目用 Redis Stream。
  - 早期项目文档里的 List 是历史口径。
- 面试要点
  - 不要把历史 List 方案说成当前实现。

### 19.1.6 消息吞吐是多少？

```text
本仓库本机压测约 700 msg/s
没有生产 8W QPS 数据
原项目 1.5W/8W 是面试口径，不是本仓库验证结果
```

## 20. 注解 API 与 AOP

### 20.1.1 @TccTransactional 做什么？

```java
@TccTransactional(
        requestId = "#{#command.requestId}",
        bizCode = "lead",
        bizId = "#{#command.leadId}",
        tryTimeoutSeconds = 30)
```

- requestId：全局事务幂等键。
- bizCode/bizId：业务定位，不负责幂等。
- tryTimeoutSeconds：Try 超时。

### 20.1.2 进入方法体之前切面做什么？

```text
解析 SpEL
manager.begin
gRPC begin TC
拿 xid
ThreadLocal current
TccContext(xid, branchId=0)
然后才执行业务方法
```

TC 此时只写：

```text
id_segment
t_tcc_global TRYING
```

还没有 branch/barrier/RM 调用。

### 20.1.3 @TccBranch 做什么？

当前过渡实现：

```text
TM 代理 registerBranch -> TC
绑定 TccContext
调 RM Try
report TRY_SUCCESS/FAIL
```

目标设计：

```text
TM 只调 RM Try
RM 在 Try 入口 registerBranch
```

### 20.1.4 AOP 代理怎么开？

```text
TccAnnotationAutoConfiguration
  @EnableAspectJAutoProxy(proxyTargetClass=true)
```

由 `AutoConfiguration.imports` 导入。

注意：

```text
LeadAssignService / quotaGateway / couponGateway 都是代理
业务代码看不到 @EnableAspectJAutoProxy
强制 CGLIB 是当前设计债
```

### 20.1.5 为什么 @TccBranch 要独立 Bean？

```text
Spring AOP 靠代理
同类 this 调用绕过代理
```

### 20.1.6 TccContext 的 branchId 会覆盖吗？

```text
会覆盖
但 branchId 已持久化到 t_tcc_branch
Confirm/Cancel 从表里读
```

限制：

```text
只支持串行分支
不支持并行/异步传播
```

## 21. 设计取舍与技术债

### 21.1.1 TM 配置 RM callbackEndpoint

- 现象
  - `@TccBranch(callbackEndpoint=...)` 写在 TM。
  - TC 从 branch 行读 endpoint。
- 问题
  - TM 知道 RM 部署地址。
  - actionName 两边手工对齐。
- 目标
  - RM 自注册 callback endpoint。
- 现状
  - 过渡实现，已记录。

### 21.1.2 分支注册职责错位

```text
当前：TM registerBranch
目标：RM self-register
```

影响：

```text
RM 要接 BranchRegistrar
Try 请求要带 xid
TM @TccBranch 要移除/降级
```

### 21.1.3 嵌套 TCC 不支持

```text
同一线程再开 @TccTransactional -> 抛异常
```

TM 兼 RM 不冲突：

```text
RM 的 Try/Confirm/Cancel 不走 TccTransactionManager
```

### 21.1.4 begin 没有自动重试

```text
begin 一次 gRPC，失败直接抛
commit/cancel 有 Scheduler
requestId 幂等，重试安全但未实现
```

### 21.1.5 branchId 命名容易误解

```text
branchId 只在 xid 内唯一
正确标识是 (xid, branchId)
日志要打 xid:branchId
```

### 21.1.6 明确未做

```text
callback 鉴权
多 TC 故障注入
ShardingSphere 分库分表
Sentinel / Prometheus
管理台/人工重放
```

## 22. 面试题

### 22.1.1 十个必答题

1. 为什么 TCC 的 Try 不由 TC 发起？
   - Try 是业务 API，需要业务参数和业务语义；TC 只认 xid/branch/action，不碰业务。
2. 为什么 Confirm/Cancel 由 TC 回调？
   - 只有 TC 知道全局状态和其他分支结果。
3. 空回滚、悬挂、幂等怎么处理？
   - Cancel 无 barrier -> SUSPENDED；迟到 Try 看到 SUSPENDED -> FAILURE；重复操作按状态直接返回。
4. owner lease 解决什么？
   - 多 TC 同时推进同一 xid 时，只允许一个 owner；fencing 防旧 owner 写。
5. 为什么 commit 的 DB 事务和 RM 回调分开？
   - 远程调用不能占 DB 事务；状态先落库，失败由 Scheduler 重试。
6. 为什么 Confirm 要写 Outbox？
   - 业务终态和消息记录同一个本地事务，防业务成功消息丢。
7. 为什么 Nacos 只注册 TC？
   - TC 是服务端；RM 的回调地址由 branch 注册元数据提供，TC 不发现 RM。
8. requestId 和 bizId 区别？
   - requestId 是 TCC 幂等键；bizId 是业务对象定位，不负责幂等。
9. 当前最大设计债是什么？
   - TM 代 RM 注册分支、TM 配置 callbackEndpoint；目标 RM 自注册。
10. 项目没做什么？
   - 不宣称 8W、不做分库分表、不做 callback 鉴权、不做多 TC 故障注入。

### 22.1.2 06 文档 66 问（按本项目口径）

> 题目来源：`xhs-demo/docs/Java Core Interview/分布式事务/06-项目文档/58CRM_-_系统分布式事务项目_.md` 开头的问题清单（1-66）。
> 答案口径：不是原文答案照抄；按当前 `58crm-transaction` 仓库实现重写，原项目数字/Redis List 口径会单独标注。
> 之前讨论过的设计点见第 28 节，不强行塞进这 66 个题号里。

1. 整体分几块？
   - TCC 协调器、TM 客户端、RM 参与方、Outbox、Redis Stream 消费者、Starter。
2. 一条消息从生产到消费经过哪些模块？
   - 业务本地事务写 Outbox -> Scanner -> Buffer -> Sender XADD -> Consumer XREADGROUP -> handler -> XACK。
3. MySQL 和 Redis 各放什么？
   - MySQL 放事务日志、消息正本、幂等、barrier；Redis Stream 只做传输。
4. 为什么两个载体？
   - MySQL 保可靠和可恢复，Redis 保吞吐和削峰。
5. TCC 三阶段谁执行？
   - TM 发起 begin/Try/commit；RM 执行 Try/Confirm/Cancel；TC 回调 Confirm/Cancel。
6. 协调器管什么？
   - global/branch 状态、owner lease、超时、重试、callback 调度。
7. 注解切面算哪层？
   - TM 接入层，负责 begin/commit/rollback 和上下文绑定。
8. 切面内部流程？
   - 解析 SpEL -> begin -> 执行业务（调 RM Try）-> commit/rollback。
9. 多业务线怎么隔离？
   - bizCode、独立 resourceKey、独立 Stream/consumer group；TCC 状态按 xid 隔离。
10. 最终一致性校验是单独模块吗？
    - 本仓库主要是 Coordinator Scheduler；对账模块未落地。
11. Try 预留什么资源？
    - quota 的 reserved、coupon 的 LOCKED；不是消息本身。
12. Confirm 具体做什么？
    - quota reserved->assigned；coupon LOCKED->USED；barrier CONFIRMED；写 Outbox。
13. Cancel 释放什么？
    - quota reserved-1；coupon LOCKED->AVAILABLE；barrier CANCELLED。
14. Try 怎么防重？
    - barrier `(xid, branchId)` 主键；已 TRIED/CONFIRMED 返回 SUCCESS。
15. Confirm 幂等怎么做？
    - barrier 已 CONFIRMED 直接 SUCCESS；否则业务动作+barrier 同事务。
16. 全局事务 ID 怎么生成？
    - MySQL 号段 `id_segment`，独立事务领号。
17. 事务日志记什么？
    - global 的 xid/requestId/biz/status/timeout/lease；branch 的 resourceKey/action/endpoint/status/retry。
18. 两个 RM 一个成功一个失败怎么办？
    - 失败分支 report TRY_FAIL；TM rollback；TC Cancel 已成功分支。
19. 先成功的资源怎么办？
    - 执行 Cancel 释放；barrier TRIED->CANCELLED。
20. 中间态对外怎么展示？
    - 业务状态由业务表决定；TCC 中间态用于框架恢复，不直接暴露给用户。
21. 超时怎么定？
    - Try 超时按业务 RT 设定；本仓库默认 30s，gRPC deadline 单独 15s。
22. 空回滚线上遇到过吗？
    - 本仓库 E2E 用 coupon 999 验证：Try 失败 barrier 回滚，Cancel 插 SUSPENDED。
23. 消息什么时候产生？
    - 业务事实和 Outbox 在同一个本地事务写入。
24. 消息第一步写哪？
    - MySQL `t_local_message`，不是 Redis。
25. 怎么和业务绑定？
    - 同一个本地事务；业务表更新 + Outbox 插入一起提交。
26. 业务回滚消息怎么办？
    - Outbox 插入一起回滚，不会留下消息。
27. 消息体字段？
    - msgId/eventKey/bizCode/bizId/eventType/payload/status。
28. Redis 用什么结构？
    - Stream，XADD/XREADGROUP/PEL/XACK。
29. 为什么不用 List？
    - List 没消费组/PEL/ACK，崩溃恢复弱；原项目历史用 List，本仓库改 Stream。
30. 推 Redis 失败怎么办？
    - Outbox 仍是 INIT，Scanner 重扫重发。
31. 批量推送怎么做？
    - Sender 批量 XADD，按批次更新状态；本仓库重点是 Stream 版本。
32. 合并窗口多长？
    - 按吞吐/延迟权衡；本仓库配置化，不宣称固定生产值。
33. 消费端怎么拿消息？
    - XREADGROUP 批量拉取。
34. 为什么批量拉？
    - 减少网络往返，提高吞吐。
35. 一批多少？
    - 配置化；本机压测按批调优到约 700 msg/s。
36. 拉出来到回调前做什么？
    - 幂等占位/检查、租约、业务 handler。
37. 重复消费第一道防线？
    - msgId 幂等键 + 消费记录/占位。
38. 占位过期时间多长？
    - 按业务执行时间设；本仓库 label/lease 续约，不能短于最长处理时间。
39. 设短会怎样？
    - 处理中占位过期，重复消息可能并发执行。
40. 还有兜底吗？
    - MySQL 唯一约束/消费记录 + 租约恢复。
41. 唯一约束建在哪？
    - 消费幂等表 `(consumer, msgId)` 或 msgId 唯一。
42. 占位后进程崩了怎么办？
    - PEL/租约恢复，重新投递或重试。
43. 消费失败去哪？
    - 重试；超限死信/失败记录。
44. 重试多少次？
    - 配置上限，退避。
45. 到头后归哪？
    - DEAD/失败表，ERROR 日志，本仓库无人工平台。
46. 多消费者怎么保证只消费一次？
    - consumer group + PEL + 幂等表；不是只靠 Redis。
47. 消息正本表怎么设计？
    - `t_local_message`，msgId/eventKey/status/retry/next_retry。
48. 索引怎么建？
    - 状态+next_retry 扫描索引；eventKey 唯一。
49. 消息表一直涨怎么办？
    - 归档/清理；本仓库未做生产级归档。
50. Redis key 怎么设计？
    - 按业务线 Stream key；消费组按业务线。
51. Redis 和 MySQL 不一致听谁的？
    - MySQL 正本；Redis 可重放。
52. Redis 挂了消息会丢吗？
    - Outbox 还在 MySQL，可重发。
53. 从哪补？
    - Scanner 扫 INIT / 重试状态。
54. 幂等为什么放两边？
    - Redis 快挡，MySQL 兜底；Redis 丢还有唯一约束。
55. 注解里有什么属性？
    - requestId/bizCode/bizId/tryTimeoutSeconds。
56. AOP 怎么找 Confirm/Cancel？
    - RM 侧 `@TccActionHandler(actionName)` 注册；TC 回调带 actionName。
57. 三个方法签名要求？
    - 统一 BusinessActionContext，返回 TccTryResult/TccPhaseResult。
58. 接口直接调用什么场景？
    - 动态分支/TccRequest builder 高级 API。
59. 两种方式同一套底层吗？
    - 是，最终都走 Coordinator + barrier 语义。
60. 业务接入要写什么？
    - `@TccTransactional` + `@TccBranch`（目标设计改为 RM 自注册）+ RM 的 TccAction。
61. 8W QPS 什么场景？
    - 原项目促销/开工季口径；本仓库未验证。
62. 日常流量和峰值？
    - 原项目 1.5W/8W；本仓库无生产数据。
63. 压测怎么做？
    - 本仓库只做本机消息组件压测，约 700 msg/s。
64. 瓶颈在哪？
    - 本机测试受 DB/Redis/线程模型影响；没有生产瓶颈结论。
65. 怎么提升吞吐？
    - 批量、缓冲、异步、减少往返；不是靠单条 RPC。
66. 队列积压怎么发现？
    - Stream lag/PEL/监控；本仓库指标能力有限。

### 22.1.3 一分钟项目介绍

```text
我做了一个跨服务一致性框架，核心是 TCC + Outbox。
TCC 解决 quota/coupon 这类多 RM 的资源预留和撤销：
TM 发起全局事务，RM 实现 Try/Confirm/Cancel，TC 维护状态并回调。
Outbox 解决业务事实发生后的可靠通知，MySQL 是正本，Redis Stream 是传输。
可靠性上做了 barrier 三防、owner lease、Scheduler 重试和 Confirm 与 Outbox 同事务。
当前真实 E2E 验证了 quota 成功、coupon 失败后 quota 被 Cancel 的双 RM 链路。
```

## 23. 白板顺序

```text
1. 画 TM / TC / RM 三个角色
2. 画 MySQL 的 global/branch/barrier/outbox
3. 画成功链路：begin -> RM register/Try -> report -> commit -> Confirm
4. 画失败链路：quota 成功、coupon 失败 -> Cancel quota
5. 补 barrier 三防
6. 补 owner lease / Scheduler
7. 最后讲设计债：RM 自注册、callback 解耦
```

## 24. 一句话

> **TCC 保证资源状态正确，Outbox 保证事实通知可靠；RM 负责自己的分支注册和回调元数据，TM 只负责发起和调用 Try。**


## 25. TCC 故障矩阵

### 25.1.1 Try 失败

```text
场景：quota 成功，coupon Try 返回 FAILURE
动作：coupon report TRY_FAIL -> TM rollback
结果：quota Cancel，coupon 空回滚 SUSPENDED
```

### 25.1.2 Try 成功但 report 丢失

```text
场景：RM 已预占，TM 没收到/没上报
状态：TC branch 仍是 TRYING
动作：TM rollback 或 TC timeout
结果：Cancel 已成功分支；未上报分支按超时收敛
```

### 25.1.3 begin 响应丢失

```text
场景：TC 已插 t_tcc_global，TM 没收到响应
动作：TM 认为 begin 失败，不执行 assign
结果：global 留 TRYING，靠 timeout 收敛
注意：当前 begin 没有自动重试
```

### 25.1.4 TC 崩溃在 CONFIRMING

```text
状态：global CONFIRMING，branch 部分 CONFIRMING
恢复：其他 TC 抢 owner lease
动作：Scheduler 继续 Confirm
结果：全部 CONFIRMED 或 DEAD
```

### 25.1.5 Confirm 重试

```text
场景：RM 超时/返回 RETRYABLE
动作：next_retry_at 退避
恢复：Scheduler 重新回调
幂等：barrier CONFIRMED 直接 SUCCESS
```

### 25.1.6 Cancel 先到

```text
场景：Cancel 比 Try 先到
动作：barrier 无记录 -> 插入 SUSPENDED
结果：返回 SUCCESS；迟到 Try 被拒绝
```

### 25.1.7 重复 Confirm/Cancel

```text
Confirm 已 CONFIRMED -> SUCCESS
Cancel 已 CANCELLED/SUSPENDED -> SUCCESS
冲突状态 -> 抛 INVALID_STATE
```

### 25.1.8 owner lease 过期

```text
场景：owner TC 卡住/崩溃
动作：lease 到期，其他 TC 抢 owner
风险：旧 owner 仍可能写
防护：更新 SQL 带 owner_token/status
```

### 25.1.9 callback endpoint 不可达

```text
场景：RM 地址不可达
动作：本次回调失败 -> RETRYABLE
恢复：Scheduler 重试
边界：当前没有 callback 鉴权
```

### 25.1.10 多分支部分失败

```text
场景：branch1 TRY_SUCCESS，branch2 TRY_FAIL
动作：rollback
结果：branch1 Cancel，branch2 空回滚/SUSPENDED
关键：不能只回滚 branch2
```

## 26. 目标设计与当前实现差异

```text
目标设计：
  RM 在 Try 入口 registerBranch
  TM 只传 xid + payload
  callbackEndpoint 由 RM 自己提供

当前仓库：
  TM 的 @TccBranch 先 registerBranch
  callbackEndpoint 写在 TM 注解
  这是过渡实现，不是目标设计

差异文档：
  docs/07-学习讨论/09-2026-11-08-分支注册职责讨论.md
```

## 27. 关联文档

```text
本文：58CRM-分布式事务与TCC.md
消息服务深入：58CRM-消息服务.md
TCC 逐段讲解：../07-学习讨论/07-2026-11-08-双RM-TCC逐段讲解.md
Barrier 三防：../07-学习讨论/11-2026-11-08-Barrier三防.md
分支注册职责：../07-学习讨论/09-2026-11-08-分支注册职责讨论.md
```


## 28. 之前讨论过的设计点

> 这些不是 06 文档原题，而是本项目学习和 review 中单独讨论出来的点。

### 28.1.1 分支注册职责：TM 还是 RM？

```text
目标：RM 在 Try 入口 registerBranch
当前：TM 的 @TccBranch 先 registerBranch
```

结论：

```text
RM 最清楚自己的 actionName/resourceKey/callbackEndpoint
TM 不应该配置 RM 部署细节
```

文档：`docs/07-学习讨论/09-2026-11-08-分支注册职责讨论.md`

### 28.1.2 TM 配置 RM callbackEndpoint

```text
当前 @TccBranch 上写 callbackEndpoint
TC 从 branch 行读取
TM 知道 RM 地址
```

结论：

```text
过渡方案，目标由 RM 自注册
```

文档：`docs/02-设计/18-TCC待办与风险.md`

### 28.1.3 @EnableAspectJAutoProxy 藏在 Starter

```text
TccAnnotationAutoConfiguration 上强制 @EnableAspectJAutoProxy(proxyTargetClass=true)
业务应用代码看不到
```

结论：

```text
代理能工作，但入口不直观，强制 CGLIB 可能覆盖用户配置
```

### 28.1.4 嵌套 TCC 与 TM 兼 RM

```text
同线程再开 @TccTransactional -> 抛 nested TCC transaction is not supported
RM 的 Try/Confirm/Cancel 不走 TccTransactionManager，不冲突
```

结论：

```text
TM 兼 RM 可以；嵌套全局 TCC 不支持
```

文档：`docs/07-学习讨论/08-2026-11-08-切面begin与上下文讨论.md`

### 28.1.5 TccContext 的 branchId 会覆盖

```text
quota Try 时 context=(xid,1)
coupon Try 时 context=(xid,2)
第一个 branchId 在 ThreadLocal 里被覆盖
```

结论：

```text
branchId 已存 t_tcc_branch，Confirm/Cancel 从表里读
TccContext 只表示当前分支
不支持并行/异步分支
```

### 28.1.6 begin 没有自动重试

```text
TccTransactionManager.begin 一次 gRPC
失败直接抛，assign 不执行
```

结论：

```text
requestId 让 begin 可安全重试，但当前没实现
```

### 28.1.7 branchId 的作用域

```text
branchId 只在 xid 内唯一
主键是 (xid, branchId)
日志必须打 xid:branchId
```

文档：`docs/07-学习讨论/10-2026-11-08-branchId作用域讨论.md`

### 28.1.8 多分支、循环分支、重复 key

```text
支持多个不同 @TccBranch
循环分支要求 resourceKey 唯一
重复 resourceKey 直接失败并回滚
```

文档：`docs/04-验证/28-C006-Multi-Branch-Spel-Review.md`

### 28.1.9 SpEL 的 # 转义

```text
#{...} 是表达式边界
表达式外 # 原样保留
"order:#123" 不解析
旧写法 "#userId"、"'quota:' + #userId" 兼容
```

文档：`docs/04-验证/28-C006-Multi-Branch-Spel-Review.md`

### 28.1.10 Barrier 三防

```text
空回滚：Cancel 无 barrier -> SUSPENDED
悬挂：迟到 Try 看到 SUSPENDED/CANCELLED -> FAILURE
幂等：重复 Try/Confirm/Cancel 按状态返回
```

文档：`docs/07-学习讨论/11-2026-11-08-Barrier三防.md`

### 28.1.11 Confirm 与 Outbox 同事务

```text
业务终态 + barrier + Outbox
同一个 RM 本地事务
```

结论：

```text
业务成功消息一定在
Refund/通知等外部动作仍要幂等
```

### 28.1.12 双 RM E2E

```text
成功：quota assigned=1，coupon USED
失败：quota Try 成功后 Cancel，coupon SUSPENDED
```

结论：

```text
证明“A 成功、B 失败、撤销 A”
```

### 28.1.13 当前边界

```text
没有 callback 鉴权
没有分库分表
没有 Sentinel/Prometheus
没有多 TC 故障注入
消息吞吐是本机约 700 msg/s，不是生产 8W
```

### 28.1.14 一句话

> **66 问是外部问题清单；第 28 节才是这个项目自己讨论出来的设计点，两者不混。**
