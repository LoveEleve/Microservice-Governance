# TCC 与消息模块联动

> 读完你能做什么：说清终态事件在 Try 还是 Confirm 写，以及为什么 TCC 项目里需要 Outbox/消息模块。

## 1. 先给结论

```text
终态业务事件：Confirm 成功后写
Try 阶段：只做资源预占和 barrier
Cancel：释放资源，不写成功事件
```

例如：

```text
LEAD_ASSIGNED
CONTRACT_EFFECTIVE
ORDER_PAID
```

这些只能发生在 Confirm 的本地事务里。

## 2. 原始文档冲突

`58CRM-分布式事务-闭环设计.md` 的正常流程写过：

```text
3. 各参与方 Try：预占配额/库存/单据
4. 业务本地事务写业务表 + 本地消息表(INIT)
5. 所有 Try 成功
6. 协调器广播 Confirm
```

这行第 4 步容易被读成：

```text
Try 成功后写消息
```

但同一套文档的 `08-业务接入示例.md` 明确写：

```text
Confirm：
  最终落库
  + 同一本地事务写 Outbox LEAD_ASSIGNED

不能：
  Try 阶段发 LEAD_ASSIGNED
```

`07-最终模型.md` 的 TCC 主流程也写：

```text
Try：
  业务预占 + barrier(TRY_SUCCESS)

Confirm：
  业务转正 + barrier(CONFIRMED)
  + 同事务写 t_local_message(INIT)
```

所以正确口径是：

```text
Try 业务本地事务：业务预占 + barrier(TRY_SUCCESS)
Confirm 本地事务：业务转正 + barrier(CONFIRMED) + Outbox(INIT)
```

`58CRM-分布式事务-闭环设计.md` 第 4 步是文档冲突/歧义，不是 Try 发终态事件的依据。

## 3. 为什么 Try 发终态事件不合理

假设 Try 写 `LEAD_ASSIGNED` 的 Outbox：

```text
Try 成功
Outbox INIT
投递器扫描 INIT
下游收到 LEAD_ASSIGNED
```

但 TCC 还没 Confirm，后续可能：

```text
另一个参与方 Try 失败
协调器 Cancel
线索回到 NEW
配额释放
```

结果：

```text
下游已经认为 LEAD_ASSIGNED 成功
实际全局事务已取消
```

这是错误的最终事实。

## 4. 如果非要在 Try 写消息，需要额外状态

必须把消息分成：

```text
PREPARED   Try 写入，但不可投递
READY      Confirm 后置为可投递
SENT       已投递
DONE       消费成功
```

Try：

```text
业务预占
+ t_local_message(PREPARED)
```

Confirm：

```text
业务转正
+ t_local_message(PREPARED -> READY)
```

投递器只扫 `READY`。

但这样实际上还是“Confirm 决定事件是否可发”，只是消息行提前插入。
当前 `t_local_message` 只有 `INIT/SENT/DONE/DEAD`，没有 `PREPARED`，所以不能直接照 Try 写。

## 5. 什么事件可以在 Try 发

可以，但必须是非终态事件，例如：

```text
LEAD_ASSIGNING
QUOTA_RESERVING
```

且必须满足：

```text
事件类型和终态事件分开
下游不会把它当成最终成功
下游有对应的取消/过期语义
```

不能把 `LEAD_ASSIGNED` 这种终态事件放 Try。

## 6. TCC Confirm 与 Outbox 的正确事务

```text
Confirm 本地事务
  BEGIN
    业务表最终状态
    t_branch_barrier(CONFIRMED)
    t_local_message(INIT)
  COMMIT
```

保证：

```text
业务提交 -> 消息一定在
业务回滚 -> 消息不存在
Confirm 重试 -> barrier + eventKey 幂等
```

之后：

```text
OutboxRelay
  -> Redis Stream
  -> 下游消费者
```

## 7. TCC 和消息模块的边界

```text
TCC 负责：
  资源预占、确认、取消
  t_tcc_global / t_tcc_branch / barrier

消息模块负责：
  Confirm 后的终态事件通知
  t_local_message / Stream / 消费幂等

Redis 负责：
  可重放传输，不存 TCC 状态
```

消息模块不可用：

```text
TCC Confirm 仍可成功
事件留在 t_local_message
恢复后继续投递
```

TCC 不可用：

```text
消息投递仍可独立运行
但不会产生新的 TCC 终态事件
```

## 8. 一句话

> **Try 只写预占和 barrier；Confirm 才写终态业务事实和 Outbox 终态事件。原始文档 Try 阶段写消息的表述是歧义，不能作为实现依据。**
