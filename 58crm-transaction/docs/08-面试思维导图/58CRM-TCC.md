# 58CRM 跨服务一致性框架面试思维导图

> 基线：`58crm-transaction` 当前仓库实现，Java 17 / Spring Boot 3.2 / MySQL / Redis Stream / Nacos / gRPC。
> 范围：项目背景、模块、架构、正确流程、状态机、Barrier、消息链路、注解 API、设计取舍、面试题。
> 用法：标题层级即思维导图节点，可直接导入支持 Markdown 的思维导图工具。
> 口径声明：
> 1. 本文流程按目标设计讲：**RM 在 Try 入口自己注册分支**，TM 不配置 RM 回调地址。
> 2. 当前仓库 `@TccBranch` 仍是 TM 先注册的过渡实现，差异见第 10 节，不把过渡实现当目标设计。
> 3. 第 11 节的 06 文档 66 问按“原项目口径 / 本仓库口径”分别标注，不混用 8W QPS 等未验证数字。

## 0. 30 秒总览

### 0.1.1 这个项目到底是什么？

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

### 0.1.2 三个角色一句话

```text
TM：发起全局事务，调 Try，决定 commit/rollback
RM：实现 Try/Confirm/Cancel，维护 barrier 和业务状态
TC：Coordinator，维护 global/branch 状态，回调 Confirm/Cancel
```

### 0.1.3 当前可验证事实

```text
mvn clean test：222 tests / 0 failures / 1 skipped
真实 E2E：quota + coupon 两个 RM
  成功：xid=1 CONFIRMED，quota assigned=1，coupon USED
  失败：xid=2 CANCELLED，quota 预占被 Cancel，coupon barrier SUSPENDED
消息组件：本机压测约 700 msg/s
```

### 0.1.4 明确不宣称

```text
没有生产 8W QPS 验证
没有 ShardingSphere 分库分表
没有 Sentinel / Prometheus / Grafana
没有 callback 鉴权 / mTLS / allowlist
没有多 TC 故障注入
```

## 1. 项目背景

### 1.1.1 业务场景是什么？

- 核心结论：CRM 线索分配同时涉及 quota（额度）和 coupon（优惠券）等多个服务。
- 核心原理
  - 线索分配不是单库事务。
  - 一个服务成功、另一个失败时，已经预占的资源必须撤销。
- 关键机制
  - 成功：两个 RM 都 Try 成功，再统一 Confirm。
  - 失败：quota Try 成功、coupon Try 失败，TC Cancel quota。
- 面试要点
  - 不要只说“用了 TCC”，要说出“A 成功、B 失败、撤销 A”的场景。

### 1.1.2 为什么本地事务不够？

- 核心结论：本地事务只能保证一个库，跨服务已经提交的操作它回滚不了。
- 核心原理
  - quota 库提交成功，coupon 库失败，本地 rollback 无法撤销 quota。
  - 普通 MQ 只能尽量送达，不能保证“业务操作 + 消息”原子。
- 关键机制
  - TCC 提供资源预留和反向 Cancel。
  - Outbox 提供业务事实和消息记录的本地原子性。
- 面试要点
  - “本地事务 + MQ”解决不了跨库原子性，也解决不了消息与业务同事务。

### 1.1.3 为什么不用纯 MQ？

- 核心结论：MQ 没有资源预留语义，也没有“撤销已经成功分支”的协议。
- 核心原理
  - MQ 是事后通知，不是事前占位。
  - 消费失败只能重试/补偿，不能阻止超卖。
- 关键机制
  - TCC 先 Try 占资源，Confirm 才落终态。
  - Outbox 只负责终态通知，不负责资源一致性。
- 面试要点
  - TCC 和 MQ 不是替代关系，是分工关系。

### 1.1.4 为什么不用 Seata？

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

### 1.1.5 项目目标与非目标

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

## 2. 模块地图

### 2.1.1 Maven 模块怎么分？

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

### 2.1.2 模块依赖方向是什么？

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

### 2.1.3 为什么把 protocol 单独拆出来？

- 核心结论：gRPC stub 和内部 DTO 不属于业务契约。
- 核心原理
  - 业务方只依赖 `tcc-api`。
  - proto 变更不应污染公共 API。
- 关键机制
  - 低层 DTO 放在 `com.wuba.crm.tx.tcc.protocol.model`。
  - stub 放在 `protocol.grpc`。
- 面试要点
  - 包边界是 public / protocol / internal 三层。

### 2.1.4 Starter 怎么分角色？

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

## 3. 架构设计

### 3.1.1 逻辑架构图

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

### 3.1.2 部署拓扑

```text
Nacos：TC 注册 gRPC 实例；TM 通过服务名发现 TC
MySQL：TC 全局/分支状态；每个 RM 自己的业务库
Redis Stream：Outbox 消息投递
Coordinator：无状态多实例，状态在 MySQL
```

### 3.1.3 传输矩阵

| 调用 | 协议 | 谁发起 |
|---|---|---|
| TM -> TC begin/commit/rollback | gRPC | TM |
| TM -> RM Try | OpenFeign / HTTP | TM |
| RM -> TC registerBranch | gRPC（目标设计） | RM |
| RM -> TC report | gRPC | RM |
| TC -> RM Confirm/Cancel | gRPC callback | TC |

### 3.1.4 为什么 TC 不发现 RM？

- 核心结论：TC 只认分支表里的回调元数据，不依赖 RM 的注册中心。
- 核心原理
  - RM 数量多、版本多、地址变化频繁。
  - TC 主动发现 RM 会把服务端和客户端耦死。
- 关键机制
  - 目标设计由 RM 注册 callback endpoint。
  - TC 从 `t_tcc_branch` 读取并回调。
- 面试要点
  - TC 是协调者，不是服务发现代理。

### 3.1.5 为什么 TM 不直接调 Confirm/Cancel？

- 核心结论：Confirm/Cancel 是 TC 根据全局状态决定的，不是业务方随意触发。
- 核心原理
  - TM 只知道 Try 结果，不知道其他分支。
  - TC 才能判断全局该 commit 还是 rollback。
- 关键机制
  - TM commit/rollback -> TC 推进状态 -> TC 回调 RM。
- 面试要点
  - Try 是业务 API，Confirm/Cancel 是 TCC 协议。

## 4. 正确流程（目标设计）

> 本节按目标设计：RM 自注册。当前仓库的 TM 注册差异见 10.2。

### 4.1.1 成功流程

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

### 4.1.2 失败流程（quota 成功、coupon 失败）

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

### 4.1.3 为什么不是 TM 注册分支？

- 核心结论：分支元数据属于 RM，TM 不该知道 RM 的 actionName/resourceKey/callbackEndpoint。
- 核心原理
  - RM 最清楚自己怎么 Try、怎么 Confirm、回调地址是什么。
  - TM 只应该传 xid 和业务 payload。
- 关键机制
  - RM 在 Try 入口注册分支。
  - TC 从 RM 注册的分支行拿 callback endpoint。
- 面试要点
  - 这是目标设计；当前仓库的 `@TccBranch` 仍是过渡实现。

### 4.1.4 当前仓库与目标设计的差异

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

## 5. 状态机、租约与重试

### 5.1.1 global 状态

```text
TRYING
  -> CONFIRMING -> CONFIRMED
  -> CANCELLING -> CANCELLED
  -> FAILED（错误日志）
```

### 5.1.2 branch 状态

```text
TRYING
  -> TRY_SUCCESS -> CONFIRMING -> CONFIRMED
  -> TRY_FAIL
  -> CANCELLING -> CANCELLED
  -> DEAD
```

### 5.1.3 barrier 状态

```text
TRIED -> CONFIRMED
      -> CANCELLED
无记录 + Cancel -> SUSPENDED
```

### 5.1.4 owner lease 解决什么？

- 核心结论：多 TC 实例同时推进同一个 xid 时，只允许一个 owner。
- 核心原理
  - `owner_token + lease_until` 在 global 行上。
  - 抢租约 SQL 带状态和过期条件。
- 关键机制
  - branch 不单独存 owner，所有 branch 跟随 global owner。
  - 旧 owner 的写用 owner_token fencing。
- 面试要点
  - 没有 lease，两个 TC 可能同时 Confirm/Cancel。

### 5.1.5 commit/rollback 为什么要分两段？

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

### 5.1.6 Scheduler 怎么重试？

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

### 5.1.7 为什么 retryable 不立刻无限重试？

- 核心结论：避免故障时打爆 RM/TC，必须有退避和上限。
- 核心原理
  - 网络抖动、RM 重启需要时间。
  - 无限重试会放大故障。
- 关键机制
  - `next_retry_at` 持久化，进程重启后仍能恢复。
  - 超限 DEAD/FAILED，ERROR 日志。
- 面试要点
  - 重试是状态机的一部分，不是 for 循环。

## 6. 数据模型

### 6.1.1 t_tcc_global

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

### 6.1.2 t_tcc_branch

```text
PK (xid, branch_id)
UK (xid, resource_key)
resource_key / action_name / callback_endpoint
status / retry_count / next_retry_at
application_data
created_at / updated_at
```

### 6.1.3 t_branch_barrier

```text
PK (xid, branch_id)
action_name
status：TRIED / CONFIRMED / CANCELLED / SUSPENDED
created_at / updated_at
```

### 6.1.4 本地消息表

```text
msg_id PK
event_key UK
biz_code / biz_id / event_type
payload
status：INIT / SENT / CONSUMED
retry_count / next_retry_at
stream_entry_id
```

### 6.1.5 XID 怎么生成？

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

### 6.1.6 branchId 的作用域

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

## 7. Barrier 三防

### 7.1.1 空回滚是什么？

- 核心结论：Cancel 先到，Try 还没来。
- 核心原理
  - 没有 barrier，直接执行 cancelAction 可能误释放。
- 关键机制
  - 插入 `SUSPENDED`，不执行 cancelAction。
  - 返回成功。
- 面试要点
  - 空回滚是“没有资源可释放”，不是失败。

### 7.1.2 悬挂是什么？

- 核心结论：Cancel 已经处理完，Try 才迟到。
- 核心原理
  - 迟到 Try 不能再占资源。
- 关键机制
  - Try 看到 `CANCELLED/SUSPENDED` 返回 FAILURE。
- 面试要点
  - `SUSPENDED` 是防悬挂标记。

### 7.1.3 幂等怎么实现？

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

### 7.1.4 Confirm 为什么必须和 Outbox 同事务？

```text
业务终态 + barrier 更新 + Outbox 插入
在同一个本地事务
```

否则：

```text
业务成功但消息丢
消息发了但业务回滚
```

### 7.1.5 为什么 Cancel 不能直接删 barrier？

- 核心结论：删了就失去幂等和防悬挂依据。
- 核心原理
  - 重复 Cancel 无法判断。
  - 迟到 Try 无法拒绝。
- 关键机制
  - 保留 `CANCELLED/SUSPENDED`。
- 面试要点
  - barrier 是状态表，不是临时锁表。

## 8. 消息链路

### 8.1.1 消息生产链路

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

### 8.1.2 为什么先写 MySQL 不直接写 Redis？

- 核心结论：MySQL 是正本，Redis 是可丢缓冲。
- 核心原理
  - Redis 挂了不能丢消息。
  - 先写本地消息表，业务和消息同事务。
- 关键机制
  - Redis 只做传输，失败可重扫。
- 面试要点
  - “先 MySQL 后 Redis”是可靠消息的核心顺序。

### 8.1.3 消费链路

```text
XREADGROUP
  -> PEL 记录
  -> 幂等检查
  -> 业务 handler
  -> XACK
  -> 消费记录/幂等表
```

### 8.1.4 消费者崩溃怎么恢复？

```text
PEL 里还有未 ACK 消息
  -> XAUTOCLAIM / 恢复任务
  -> 重新投递或重试
```

### 8.1.5 为什么不用 Redis List？

- 核心结论：List 没有消费组/PEL/ACK，崩溃恢复弱。
- 核心原理
  - Stream 有 consumer group、pending、ack。
  - List 弹出即丢失。
- 关键机制
  - 本项目用 Redis Stream。
  - 早期项目文档里的 List 是历史口径。
- 面试要点
  - 不要把历史 List 方案说成当前实现。

### 8.1.6 消息吞吐是多少？

```text
本仓库本机压测约 700 msg/s
没有生产 8W QPS 数据
原项目 1.5W/8W 是面试口径，不是本仓库验证结果
```

## 9. 注解 API 与 AOP

### 9.1.1 @TccTransactional 做什么？

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

### 9.1.2 进入方法体之前切面做什么？

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

### 9.1.3 @TccBranch 做什么？

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

### 9.1.4 AOP 代理怎么开？

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

### 9.1.5 为什么 @TccBranch 要独立 Bean？

```text
Spring AOP 靠代理
同类 this 调用绕过代理
```

### 9.1.6 TccContext 的 branchId 会覆盖吗？

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

## 10. 设计取舍与技术债

### 10.1.1 TM 配置 RM callbackEndpoint

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

### 10.1.2 分支注册职责错位

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

### 10.1.3 嵌套 TCC 不支持

```text
同一线程再开 @TccTransactional -> 抛异常
```

TM 兼 RM 不冲突：

```text
RM 的 Try/Confirm/Cancel 不走 TccTransactionManager
```

### 10.1.4 begin 没有自动重试

```text
begin 一次 gRPC，失败直接抛
commit/cancel 有 Scheduler
requestId 幂等，重试安全但未实现
```

### 10.1.5 branchId 命名容易误解

```text
branchId 只在 xid 内唯一
正确标识是 (xid, branchId)
日志要打 xid:branchId
```

### 10.1.6 明确未做

```text
callback 鉴权
多 TC 故障注入
ShardingSphere 分库分表
Sentinel / Prometheus
管理台/人工重放
```

## 11. 面试题

### 11.1.1 十个必答题

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

### 11.1.2 06 文档 66 问（按本项目口径）

> 06 文档的原始项目是 Redis List + 8W QPS 口径；本仓库是 Redis Stream + 本机验证实现。涉及数字的题目标注“原项目口径”。

1. 整体分几块？
   - TCC 协调器、TM 客户端、RM 参与方、Outbox、Redis Stream 消费者、Starter。
2. 一条消息从生产到消费经过哪些模块？
   - 业务本地事务写 Outbox -> Scanner -> Buffer -> Sender XADD -> Consumer XREADGROUP -> handler -> XACK。
3. MySQL 和 Redis 各放什么？
   - MySQL 放事务日志、消息正本、幂等、barrier；Redis 放 Stream 传输和幂等加速。
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

### 11.1.3 一分钟项目介绍

```text
我做了一个跨服务一致性框架，核心是 TCC + Outbox。
TCC 解决 quota/coupon 这类多 RM 的资源预留和撤销：
TM 发起全局事务，RM 实现 Try/Confirm/Cancel，TC 维护状态并回调。
Outbox 解决业务事实发生后的可靠通知，MySQL 是正本，Redis Stream 是传输。
可靠性上做了 barrier 三防、owner lease、Scheduler 重试和 Confirm 与 Outbox 同事务。
当前真实 E2E 验证了 quota 成功、coupon 失败后 quota 被 Cancel 的双 RM 链路。
```

## 12. 白板顺序

```text
1. 画 TM / TC / RM 三个角色
2. 画 MySQL 的 global/branch/barrier/outbox
3. 画成功链路：begin -> RM register/Try -> report -> commit -> Confirm
4. 画失败链路：quota 成功、coupon 失败 -> Cancel quota
5. 补 barrier 三防
6. 补 owner lease / Scheduler
7. 最后讲设计债：RM 自注册、callback 解耦
```

## 13. 一句话

> **TCC 保证资源状态正确，Outbox 保证事实通知可靠；RM 负责自己的分支注册和回调元数据，TM 只负责发起和调用 Try。**
