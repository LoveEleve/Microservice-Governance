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

## 8. Participant 的连接

RM 在 Try 注册时保存：

```text
endpoint
action_name
resource_key
application_data
```

Coordinator Confirm/Cancel：

```text
按 branch.endpoint/action_name 调 RM
不直接操作 RM 业务表
```

endpoint 来源：

```text
Nacos 服务发现
或配置的固定地址
```

第一版可以先做固定 endpoint，后续接 Nacos。

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
4. 没有 participant endpoint 注册
5. 没有 Nacos 服务发现
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
  Coordinator/Participant DTO

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
```

## 12. 一句话

> **Seata 有 seata-server，DTM 有 dtmsvr；我们的 TCC 也必须有一个独立的 Coordinator Server。业务应用里只放 SDK/RM，服务端只放 TC 状态机、调度和恢复。**
