# TCC 二阶段与 Callback

> 读完你能做什么：知道 Commit/Rollback 如何拿 owner、预推进 branch、调用 RM，以及失败如何收敛。

## 1. 范围

```text
Commit
Rollback
Query
TC -> RM Confirm/Cancel
owner lease
```

不在本切片：

```text
Participant barrier 实现
Scheduler 自动重试
Outbox 同事务
```

## 2. 二阶段流程

Commit：

```text
1. 事务内锁 global
2. 检查 owner lease
3. 检查 branch 状态
4. global TRYING -> CONFIRMING
5. branch TRY_SUCCESS -> CONFIRMING
6. 提交 DB 事务
7. 锁外调用 RM Confirm
8. branch CONFIRMING -> CONFIRMED
9. 全部完成 -> global CONFIRMED
```

Rollback：

```text
1. 事务内锁 global
2. global TRYING -> CANCELLING
3. branch TRYING/TRY_SUCCESS/TRY_FAIL -> CANCELLING
4. 提交 DB 事务
5. 锁外调用 RM Cancel
6. branch CANCELLING -> CANCELLED
7. 全部完成 -> global CANCELLED
```

## 3. owner lease

```text
owner_token 绑定 TC 实例
lease_until 限制 owner 有效期
lease 30s，回调前续租
```

规则：

```text
owner 有效 -> TCC_BUSY
owner 过期 -> 新 TC 可接管
transitionOwned/markConfirmed/markCancelled/markDead
  都必须校验 owner + lease
```

## 4. RM callback

接口：

```text
TccParticipantClient
  confirm(branch)
  cancel(branch)
```

gRPC 实现：

```text
TccParticipantCallbackGrpc
  Confirm
  Cancel
```

endpoint：

```text
host:port
http://host:port/path
```

第一版明文 gRPC，不鉴权。

## 5. 回调结果

```text
SUCCESS
  -> branch 终态
RETRYABLE
  -> 保持 CONFIRMING/CANCELLING，等 Scheduler
PERMANENT_FAILURE
  -> branch DEAD
  -> global FAILED
```

## 6. 事务边界

```text
DB 事务只做状态推进
网络回调在 DB 事务外
```

原因：

```text
RM 可能慢/不可达
不能持有 global 行锁等待网络
```

## 7. Query

```text
global + branches
只读，不加 owner
```

## 8. 当前限制

```text
Cancel 成功不区分 CANCELLED / SUSPENDED
  -> C-003.4 由 barrier 和 participant 状态补充

RETRYABLE 不立即重试全部完成
  -> C-003.6 Scheduler

callback 每次新建 channel
  -> 后续做 channel 复用/TLS
```

## 9. 一句话

> **Commit/Rollback 先在 DB 事务里抢 owner、推进 global/branch，再在事务外回调 RM；owner、lease 和 branch 状态条件共同防止旧 TC 覆盖新状态。**
