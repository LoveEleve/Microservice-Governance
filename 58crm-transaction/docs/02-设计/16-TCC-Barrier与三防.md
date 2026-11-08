# TCC Barrier 与三防

> 读完你能做什么：说清幂等、空回滚、悬挂在 RM 本地事务里如何用一张 barrier 表解决。

## 1. 业务例子

线索分配：

```text
Try：
  lead NEW -> ASSIGNING
  quota reserved + 1

Confirm：
  lead ASSIGNED
  quota 正式扣减

Cancel：
  lead -> NEW
  quota reserved - 1
```

问题：

```text
Confirm 重发怎么办？
Cancel 先到怎么办？
Try 后到怎么办？
```

barrier 是 RM 业务库里的本地状态闸门。

## 2. 表

```text
t_branch_barrier
  xid
  branch_id
  action_name
  status
  created_at
  updated_at
  PRIMARY KEY (xid, branch_id)
```

状态：

```text
TRIED
CONFIRMED
CANCELLED
SUSPENDED
```

## 3. Try

```text
BEGIN
  INSERT barrier(xid, branch_id, TRIED)
  + 业务预占
COMMIT
```

同一个本地事务。

重复 Try：

```text
TRIED       -> 幂等返回成功
CONFIRMED   -> 已最终提交，返回成功，不再预占
CANCELLED   -> 拒绝
SUSPENDED   -> 拒绝
```

## 4. Confirm

```text
BEGIN
  SELECT barrier FOR UPDATE

  不存在
    -> 拒绝，不能凭空 Confirm

  CONFIRMED
    -> 幂等成功

  CANCELLED / SUSPENDED
    -> 拒绝

  TRIED
    -> UPDATE CONFIRMED
    + 业务最终状态
    + Outbox(INIT)
COMMIT
```

## 5. Cancel

```text
BEGIN
  SELECT barrier FOR UPDATE

  不存在
    -> INSERT SUSPENDED
    -> 返回成功

  SUSPENDED / CANCELLED
    -> 幂等成功

  CONFIRMED
    -> 拒绝

  TRIED
    -> UPDATE CANCELLED
    + 释放预占
COMMIT
```

## 6. 三防

```text
幂等：
  Try 重复 / Confirm 重复 / Cancel 重复

空回滚：
  Cancel 先到，没有 Try
  -> SUSPENDED

悬挂：
  Cancel 留下 SUSPENDED
  -> 迟到的 Try 被拒绝
```

## 7. 并发

Cancel 与 Try 同时到达：

```text
唯一键(xid, branch_id)决定顺序
Try 先插入 -> TRIED -> Cancel 释放
Cancel 先插入 -> SUSPENDED -> Try 拒绝
```

Confirm 与 Cancel 同时到达：

```text
SELECT ... FOR UPDATE
只能有一个方向
TRIED -> CONFIRMED
或
TRIED -> CANCELLED
```

## 8. 决策

```text
D-018 barrier 单行状态机
D-019 Confirm 无 barrier 拒绝
D-020 Cancel 无 barrier 插入 SUSPENDED
D-021 Try 在 CANCELLED/SUSPENDED 后拒绝
D-022 barrier 与业务、Outbox 同本地事务

说明：Outbox 由业务 TccAction.confirmAction 调用 OutboxPublisher 写入，
框架只负责提供同一个本地事务，不生成业务事件。
```

## 9. 一句话

> **barrier 是 RM 本地事务里的状态闸门：TRIED 表示 Try 成功，CONFIRMED/CANCELLED 表示终态，SUSPENDED 表示 Cancel 先到的空回滚标记；迟到的 Try 看到 SUSPENDED 必须拒绝。**
