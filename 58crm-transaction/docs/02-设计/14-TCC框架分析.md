# TCC 框架分析：Seata TCC 与 DTM

> 参考源码：
> `source-code/code/spring/seata-2.0.0/tcc`
> `source-code/code/spring/seata-2.0.0/integration-tx-api`
> `source-code/code/dtm/client/dtmcli`
> `source-code/code/dtm/dtmsvr`
>
> 结论：TCC 只负责资源两阶段；消息服务只负责 Confirm 后的事实通知。两者通过 Confirm 本地事务连接，不共用状态表。

## 1. Seata TCC 的实现

### 1.1 Try 入口

```text
@TwoPhaseBusinessAction
  -> TccActionInterceptorHandler
  -> ActionInterceptorHandler.proceed
  -> DefaultResourceManager.branchRegister
  -> fence.prepareFence
  -> 业务 Try 方法
```

关键点：

```text
Try 方法被代理
分支注册在 Try 执行前
BusinessActionContext 透传 xid/branchId/actionName/业务参数
```

### 1.2 二阶段

```text
TC 调 RM
  -> RMHandlerTCC
  -> TCCResourceManager.branchCommit / branchRollback
  -> commitMethod / rollbackMethod
```

返回：

```text
PhaseTwo_Committed
PhaseTwo_CommitFailed_Retryable
PhaseTwo_Rollbacked
PhaseTwo_RollbackFailed_Retryable
```

失败由 TC 重试。

### 1.3 Fence / Barrier

Seata 的 `SpringFenceHandler` + `tcc_fence_log`：

```text
STATUS_TRIED      1
STATUS_COMMITTED  2
STATUS_ROLLBACKED 3
STATUS_SUSPENDED  4
```

Try：

```text
insert tcc_fence_log(xid, branch_id, action_name, TRIED)
+ 业务 Try
同一个本地事务
```

Confirm：

```text
select ... for update
已 COMMITTED -> 幂等返回
ROLLBACKED/SUSPENDED -> 拒绝
TRIED -> update COMMITTED + 调业务 Confirm
同一个本地事务
```

Cancel：

```text
没有记录 -> insert SUSPENDED（空回滚）
已 ROLLBACKED/SUSPENDED -> 幂等
COMMITTED -> 拒绝
TRIED -> update ROLLBACKED + 调业务 Cancel
```

这解决三件事：

```text
幂等
空回滚
悬挂
```

## 2. DTM TCC 的实现

### 2.1 客户端

```text
TccGlobalTransaction
  -> prepare
  -> tccFunc
       -> CallBranch 注册 try/confirm/cancel URL
       -> 调 Try
  -> submit
  -> abort（失败）
```

服务端 TCC 状态：

```text
prepared
submitted
succeed
aborting
failed
```

### 2.2 服务端

`transTccProcessor.ProcessOnce`：

```text
prepared + timeout -> aborting
submitted -> 倒序执行 confirm
aborting -> 倒序执行 cancel
```

分支状态：

```text
prepared -> succeed / failed
```

### 2.3 Barrier

DTM `BranchBarrier.Call`：

```text
在一个本地事务里：
  插入 origin barrier
  插入 current barrier
  根据 affected rows 判断：
    重复请求
    空补偿
    悬挂
  执行业务
```

核心是 `barrier` 表唯一约束和 `origin/current` 两条记录。

对比 Seata：

```text
Seata：tcc_fence_log + 状态机
DTM：barrier 表 + origin/current 幂等标记
目标一样：幂等、空回滚、悬挂
```

## 3. 对比

| 维度 | Seata TCC | DTM TCC | 我们的目标 |
|---|---|---|---|
| Try 入口 | 注解 + AOP | 显式 TCC 调用 | 注解 + Template |
| 分支注册 | TC branchRegister | 服务端分支表 | MySQL t_tcc_branch |
| 二阶段调用 | TC -> RM | DTM -> HTTP/gRPC | Coordinator -> Participant |
| 幂等/防悬挂 | tcc_fence_log | barrier 表 | t_branch_barrier |
| 恢复 | TC 重试 | cron 扫描 | scheduler + lease |
| 状态存储 | TC 库/日志 | SQL/Redis/Bolt | MySQL |
| 消息 | 不在 TCC 内核 | 不在 TCC 内核 | 独立 Outbox |
| 部署 | Seata Server | DTM Server | 自研 Coordinator |

## 4. 我们的 TCC 设计

### 4.1 表

```text
t_tcc_global
  xid PK
  request_id UK
  biz_code/biz_id
  status
  timeout_at
  retry_count
  next_retry_at
  created_at/updated_at

t_tcc_branch
  xid + branch_id PK
  UNIQUE(xid, resource_key)
  action_name
  status
  retry_count
  next_retry_at
  created_at/updated_at

t_branch_barrier（每个 RM 业务库）
  xid + branch_id PK
  action_name
  status
  created_at/updated_at
```

### 4.2 状态

全局：

```text
TRYING
CONFIRMING
CONFIRMED
CANCELLING
CANCELLED
```

分支：

```text
TRYING
TRY_SUCCESS
TRY_FAIL
CONFIRMING
CONFIRMED
CANCELLING
CANCELLED
SUSPENDED
```

Barrier：

```text
TRIED
CONFIRMED
CANCELLED
SUSPENDED
```

### 4.3 流程

```text
Begin
  -> t_tcc_global(TRYING)

Try
  -> RM 本地事务：
       业务预占
       + t_branch_barrier(TRIED)
  -> 上报 TRY_SUCCESS

Commit
  -> global CONFIRMING
  -> 逐分支 Confirm
       RM 本地事务：
         业务最终状态
         + barrier(TRIED -> CONFIRMED)
         + outbox(INIT)（终态事件）
  -> global CONFIRMED

Cancel
  -> global CANCELLING
  -> 逐分支 Cancel
       RM 本地事务：
         释放预占
         + barrier(TRIED -> CANCELLED)
         或 barrier 不存在 -> SUSPENDED
  -> global CANCELLED
```

### 4.4 恢复

```text
扫描：
  TRYING 超时
  TRY_SUCCESS 长时间未 Confirm
  CONFIRMING/CANCELLING 未完成

动作：
  重发 Confirm/Cancel
  指数退避
  超过上限 -> DEAD/人工
```

扫描必须有租约：

```text
owner_token + lease_until
```

避免多个 coordinator 同时推进同一事务。

## 5. 与消息模块的连接

只在 Confirm：

```text
Confirm 本地事务
  业务最终状态
  + barrier(CONFIRMED)
  + t_local_message(INIT)
COMMIT
```

之后：

```text
OutboxScanner -> Redis Stream -> 下游
```

Try：

```text
只做资源预占
不写终态消息
```

Cancel：

```text
只释放资源
不写成功事件
```

消息服务的状态：

```text
INIT / SENT / DONE / DEAD
```

TCC 的状态：

```text
TRY / CONFIRM / CANCEL
```

两套状态绝不混表。

## 6. 参考后的取舍

采用：

```text
Seata 的 fence 状态机
DTM 的 barrier 幂等思路
自研 Coordinator 的 MySQL 状态表
Confirm 同事务写 Outbox
```

不采用：

```text
Seata 的全局 TC 集群（项目规模不需要）
DTM 的 HTTP/gRPC 分支 URL 注册（先做本地/标准 RPC）
TCC 状态放 Redis
Try 发终态事件
```

## 7. 实施切片

```text
C-003.1
  TccStatus / BusinessActionContext
  t_tcc_global / t_tcc_branch / t_branch_barrier
  TccAction SPI

C-003.2
  TccCoordinator begin/register/report
  commit/cancel 状态机

C-003.3
  Participant barrier
  空回滚 / 悬挂 / Confirm-Cancel 幂等

C-003.4
  Confirm + Outbox 同事务
  终态事件联调

C-003.5
  timeout scheduler / lease / retry / dead letter
```
