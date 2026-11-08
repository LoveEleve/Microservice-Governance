# TCC 服务器架构

> 读完你能做什么：说清 TCC 为什么需要独立服务器、服务器与 SDK/参与方怎么分工，以及本项目的部署与接口边界。

## 1. Seata / DTM 的部署边界

Seata：

```text
seata-server（TC，独立进程）
  ├── 全局事务/分支事务状态
  ├── Confirm/Cancel 调度
  └── 重试/恢复
业务应用内的 TM/RM 客户端
  ├── begin/commit/rollback
  ├── branchRegister/report
  └── TCC Try/Confirm/Cancel 代理
```

DTM：

```text
dtmsvr（独立服务）
  ├── 全局事务表/分支表
  ├── HTTP/gRPC 分支调用
  └── cron 重试/超时
dtmcli / dtmgrpc（客户端 SDK）
  ├── prepare/submit/abort
  └── barrier 本地事务
```

结论：

```text
TCC 协调器是服务器，不是业务应用内的本地组件。
```

## 2. 本项目的模块边界

```text
crm-tx-tcc-api
  TccStatus / 错误码 / 公共模型
  TccAction / BusinessActionContext / TccRequest / TccResult
  TccTransactionTemplate 接口
  Coordinator 与 Participant 的 DTO

crm-tx-tcc-coordinator
  Spring Boot 独立服务
  TC 类角色
  全局/分支状态机
  重试/恢复调度

crm-tx-tcc-participant
  RM SDK
  TccAction 执行
  barrier
  Confirm 写 Outbox

crm-tx-spring-boot-starter
  参与方/业务方自动装配
  不装协调器状态存储
```

禁止：

```text
把 Coordinator 状态放业务应用内存
每个业务应用自己跑一个 Coordinator 并共享业务库
Coordinator 直接写参与方业务表
```

## 2.1 传输矩阵

```text
TM -> TC
  gRPC unary
  begin / register / report / commit / rollback / query

TC -> RM
  gRPC unary callback
  RM 在 branch register 时提供 callback endpoint
  TC 不发现 RM
```

不使用：

```text
TC 查 Nacos 发现 RM
TC 配置业务方 serviceName/endpoint
为了“全一元”让 TC 主动连 RM
```

## 3. 部署拓扑

```text
                    Nacos
                      │
业务应用 A ─┐         │
业务应用 B ─┼──> Coordinator Cluster（5 实例，无状态）
业务应用 C ─┘         │
                      ├── MySQL 主库（全局/分支状态）
                      ├── MySQL 从库（查询）
                      └── Scheduler/Lease

Participant SDK
  └── 业务库：t_branch_barrier + t_local_message
  └── 暴露 Confirm/Cancel callback endpoint
```

原则：

```text
Coordinator 无状态
状态在 Coordinator MySQL
参与方 barrier/outbox 在参与方业务库
Coordinator 不跨库写参与方业务表
```

## 4. 服务器 API

Coordinator 对外：

```text
TccCoordinator.Begin
TccCoordinator.RegisterBranch
TccCoordinator.ReportBranch
TccCoordinator.Commit
TccCoordinator.Rollback
TccCoordinator.Query
```

Participant 回调：

```text
TccParticipantCallback.Confirm
TccParticipantCallback.Cancel
```

全部为 gRPC unary；proto 见 `20-TCC-API与Proto.md`。

endpoint 由 RM 在 branch register 时提供，不是 TC 配置。

Try 由业务方通过业务 API 调用，不由 Coordinator 发起。

## 5. 数据库表

最终字段、索引和 SQL 见 `21-TCC表结构与SQL.md`。

```text
t_tcc_global
  xid / request_id / biz_code / biz_id
  status / timeout_at / branch_seq
  owner_token / lease_until
  created_at / updated_at

t_tcc_branch
  xid + branch_id
  resource_key / action_name / callback_endpoint
  status / retry_count / next_retry_at
  application_data
  created_at / updated_at

t_branch_barrier
  xid + branch_id
  action_name / status
  created_at / updated_at
```

## 6. Coordinator 状态机

```text
全局：
  TRYING -> CONFIRMING -> CONFIRMED
         -> CANCELLING -> CANCELLED
         -> FAILED（ERROR 日志，不人工改状态）

分支：
  TRYING -> TRY_SUCCESS -> CONFIRMING -> CONFIRMED
         -> TRY_FAIL
         -> CANCELLING -> CANCELLED/SUSPENDED
         -> DEAD
```

状态更新必须：

```text
WHERE xid=? AND status=?
```

CAS 影响行数为 0，说明并发推进，不重复执行。

## 7. 服务器恢复与租约

Scheduler 扫描：

```text
TRYING 超时
CONFIRMING / CANCELLING 的 branch.next_retry_at 到期
```

拿 global 租约：

```text
UPDATE t_tcc_global
SET owner_token=?, lease_until=?
WHERE xid=?
  AND status IN ('TRYING','CONFIRMING','CANCELLING')
  AND (owner_token IS NULL OR lease_until < NOW(3))
```

只有拿到租约的实例可以推进/重试；branch 不单独存 owner。

退避：

```text
1s / 5s / 30s / 2min / 10min
```

超限：

```text
DEAD
global FAILED
ERROR 日志
```

## 8. Participant 的连接：TC 不发现 RM

错误方案：

```text
Coordinator 查 Nacos
-> 找 Participant 的 serviceName
-> 主动调用 Confirm/Cancel
```

这会让服务器依赖客户端，并迫使 TC 配置使用方的服务名，扩展性差。

### 8.1 第一版：callback endpoint

采用 DTM 风格：

```text
RM 在 branch/register 时提供：
  callbackEndpoint
  actionName
  resourceKey
```

TC 任意实例：

```text
按 branch.callbackEndpoint
调用 Confirm/Cancel
```

branch 保存：

```text
callbackEndpoint
actionName
resourceKey
applicationData
```

callbackEndpoint 必须落 `t_tcc_branch`，不能只存在某个 TC 的内存：

```text
tc-0 registerBranch
  -> INSERT t_tcc_branch(callback_endpoint=...)

tc-3 commit
  -> SELECT branch FROM t_tcc_branch
  -> 读 callback_endpoint
  -> 调 RM
```

所以：

```text
注册在 tc-0
commit 到 tc-3
仍然能回调 RM
```

不需要 sticky，不需要连接亲和性。

不保存：

```text
RM 的 Nacos serviceName
TC 侧的客户端服务配置
```

TC 不发现 RM，也不配置业务方 serviceName。
endpoint 是客户端在注册时主动提供的回调地址。

endpoint 要求：

```text
稳定服务地址（K8s Service / 内网 LB）
不要用 Pod IP
第一版按内网可信，不做鉴权
```

branch register 幂等：

```text
UNIQUE(xid, resource_key)
重复 register 返回已有 branch
不允许未授权修改 callback_endpoint
```

代价：

```text
RM 需要暴露 Confirm/Cancel 服务（gRPC/HTTP）
需要处理端口/安全/内网可达
```

### 8.2 Seata 的做法：客户端连接所有 TC

Seata 并不是没有这个问题，它的方案是：

```text
TM/RM 客户端连接所有 TC 实例
每个 TC 都持有到 RM 的 channel
任意 TC 都能下发 Confirm/Cancel
```

源码依据：

```text
core/.../NettyClientChannelManager.reconnect
  -> lookup 所有 TC 地址
  -> for each serverAddress: acquireChannel
```

所以 Seata 的 A 方案是：

```text
用连接数换实现简单
```

代价：

```text
连接数 = 客户端实例数 × TC 数
TC 扩容需要客户端重连
```

5 个固定 TC、客户端数量可控时可以接受；TC 数量很大或客户端很多时会膨胀。

### 8.3 DTM 的做法：callback URL

DTM 不维护 TC->RM 长连接，而是：

```text
RM 注册分支时提供 confirm/cancel URL
DTM 服务端按 URL 回调
```

也就是本项目的 callback 方案：

```text
RM 在 branch register 时提供 callbackEndpoint
TC 任意实例按 endpoint 调 Confirm/Cancel
```

优点：

```text
TC 实例之间不共享 stream registry
RM 不需要连接所有 TC
```

代价：

```text
RM 需要暴露 callback 服务
需要端口/安全/内网可达
```

### 8.4 本项目选择：B（callback）

```text
TM -> TC：gRPC unary
TC -> RM：gRPC unary callback
```

branch 保存：

```text
callbackEndpoint
actionName
resourceKey
```

选择理由：

```text
TC 会扩缩容
不维护客户端连接表
不做 TC 内部 stream 转发
不做 xid sticky
```

RM callback endpoint 要求：

```text
稳定服务地址：K8s Service / 内网 LB
不使用 Pod IP
内网可达
第一版不做鉴权
```

不选：

```text
TC 查 Nacos 发现 RM
TC 配置业务方 serviceName
RM 连接所有 TC
Spring Cloud LoadBalancer 解决 TC->RM
```

Spring Cloud LoadBalancer 只选一个 TC 实例，不能保证 Confirm 落到持有 channel 的 TC；除非做 sticky/consistent hash，但会引入故障迁移问题。

A（Seata 风格）保留为后续性能对照：

```text
只有压测证明 callback 网络/端口成本不可接受时再评估
```

## 9. TCC 与消息

Confirm 的 RM 本地事务：

```text
业务最终状态
+ barrier(CONFIRMED)
+ t_local_message(INIT)
COMMIT
```

Coordinator 不写 Outbox。

消息状态：

```text
INIT / SENT / DONE / DEAD
```

TCC 状态：

```text
TRY / CONFIRM / CANCEL
```

两套状态不共用表。

## 10. 深 review 发现的原规划缺口

```text
1. 只写了 Coordinator 组件，没有独立 server 模块
2. 没有 API/传输协议
3. 没有 Coordinator 集群/租约/选主
4. 没有 RM callback endpoint 注册协议
5. 没有明确 Nacos 只发现 TC、不发现 RM
6. 没有 scheduler 扫描条件
7. 没有 server 级幂等键
8. 没有 server 监控/告警（第一版明确不做）
9. 没有 server 与 participant 部署边界
10. 没有多实例协调器测试
```

## 11. 实施切片

```text
C-003.1
  crm-tx-tcc-api
  TccStatus / TccAction / BusinessActionContext
  Coordinator unary DTO
  Participant callback DTO
  proto 契约（Begin/Register/Report/Commit/Rollback）
  callback 契约（Confirm/Cancel）

C-003.2
  crm-tx-tcc-coordinator 服务器
  begin/register/report
  t_tcc_global / t_tcc_branch

C-003.3
  commit/rollback
  participant confirm/cancel 调用
  幂等 + CAS

C-003.4
  t_branch_barrier
  空回滚 / 悬挂 / Confirm-Cancel 幂等

C-003.5
  Confirm + Outbox 同事务

C-003.6
  Scheduler + lease + retry
  Nacos 接入
  多实例测试
  callback endpoint 注册（第一版不做鉴权）
```

## 12. 一句话

> **Seata 有 seata-server，DTM 有 dtmsvr；我们的 TCC 也必须有一个独立的 Coordinator Server。业务应用里只放 SDK/RM，服务端只放 TC 状态机、调度和恢复。**
