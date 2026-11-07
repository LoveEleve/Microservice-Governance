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
crm-tx-common
  TccStatus / 错误码 / 公共模型

crm-tx-tcc-api
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
POST /tcc/begin
POST /tcc/branch/register
POST /tcc/branch/report
POST /tcc/commit
POST /tcc/rollback
GET  /tcc/{xid}
```

Participant 回调：

```text
POST /tcc/branch/confirm
POST /tcc/branch/cancel
```

endpoint 由 RM 在 branch register 时提供，不是 TC 配置。

Try 由业务方通过 SDK/分支代理调用，不强制由 Coordinator 发起。

## 5. 数据库表

### Coordinator

```text
t_tcc_global
  xid PK
  request_id UK
  biz_code
  biz_id
  status
  timeout_at
  retry_count
  next_retry_at
  owner_token
  lease_until
  version
  created_at
  updated_at

t_tcc_branch
  xid + branch_id PK
  UNIQUE(xid, resource_key)
  action_name
  endpoint
  resource_key
  status
  retry_count
  next_retry_at
  owner_token
  lease_until
  version
  application_data
  created_at
  updated_at
```

### Participant

```text
t_branch_barrier
  xid + branch_id PK
  action_name
  status
  created_at
  updated_at
```

## 6. Coordinator 状态机

```text
全局：
  TRYING -> CONFIRMING -> CONFIRMED
         -> CANCELLING -> CANCELLED
         -> FAILED（人工）

分支：
  TRYING -> TRY_SUCCESS -> CONFIRMING -> CONFIRMED
         -> TRY_FAIL
         -> CANCELLING -> CANCELLED
         -> SUSPENDED
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
TRY_SUCCESS 长时间未 Confirm
CONFIRMING / CANCELLING 停滞
```

拿租约：

```text
UPDATE t_tcc_global
SET owner_token=?, lease_until=?
WHERE xid=? AND status=? AND lease_until < now()
```

只有拿到租约的实例可以推进/重试。

退避：

```text
1s / 5s / 30s / 2min / 10min
```

超限：

```text
DEAD / 人工队列
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

不保存：

```text
RM 的 Nacos serviceName
TC 侧的客户端服务配置
```

TC 不发现 RM，也不配置业务方 serviceName。
endpoint 是客户端在注册时主动提供的回调地址。

代价：

```text
RM 需要暴露 Confirm/Cancel 服务（gRPC/HTTP）
需要处理端口/安全/内网可达
```

### 8.2 为什么不用 bidi stream

如果 RM 只连接一个 TC：

```text
Confirm 请求落到另一个 TC
那个 TC 没有这条 stream
-> 发不出去
```

如果 RM 连接所有 TC：

```text
连接数按 RM 实例数 × TC 数膨胀
TC 扩容要所有 RM 重连
注册表分散在 5 个 TC
```

所以第一版不做 bidi control stream，也不做 stream owner 转发。

### 8.3 备选：Seata 风格单控制流

如果后续 RM 不方便暴露 callback 端口，再做：

```text
RM 只连接一个 TC
TC 集群内部维护 stream owner
其它 TC 把命令转给 owner
```

优点：RM 不需要暴露端口。
缺点：TC 内部需要 stream 路由/转发，复杂度更高。

第一版选择：

```text
TM -> TC：unary
TC -> RM：unary callback（客户端注册 endpoint）
```

不在第一版做：

```text
RM 连接所有 TC
TC 内部 stream 转发
sticky xid 到 TC
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
8. 没有 server 监控/告警
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
  callback endpoint 注册/鉴权
```

## 12. 一句话

> **Seata 有 seata-server，DTM 有 dtmsvr；我们的 TCC 也必须有一个独立的 Coordinator Server。业务应用里只放 SDK/RM，服务端只放 TC 状态机、调度和恢复。**
