# TCC 与消息模块联动

> 读完你能做什么：说清标准文档为什么在 Try 写消息、这样做的风险，以及终态事件应该怎么落地。

## 1. 标准文档口径

以 `58CRM_-_系统分布式事务项目_.md` 为准：

```text
业务方执行 Try 成功后，
消息服务在同一数据库事务内插入本地消息表（状态 INIT）
```

标准文档还写：

```text
Try 执行成功的同时，
消息在业务那笔本地事务里写进本地消息表，状态 INIT
```

并且：

```text
Confirm 和消息消费是两回事
消息解决下游服务怎么知道这件事发生了
Confirm 解决参与方自己的预留资源怎么落定
两者互不阻塞
```

所以标准文档的设计是：

```text
Try 成功后写本地消息
消息消费和 Confirm 并行
```

不是 Confirm 后写消息。

`项目参数-叶_.md` 只描述投递层和 Redis List，没有改变这个阶段口径。

## 2. 消息服务关心终态吗

消息服务本身不判断 TCC 阶段。

它只负责：

```text
消息正本
投递
重试
消费
幂等
```

但事件必须区分语义：

```text
ATTEMPT    处理中/资源预占
FINAL      最终成功
CANCELLED  最终取消
```

如果消息服务把所有事件都当成 FINAL，就会把“预占成功”误当成“业务成功”。

## 3. 标准做法的问题

Try 写 `LEAD_ASSIGNED`（终态事件）：

```text
Try 成功
消息 INIT
投递器发送
下游收到 LEAD_ASSIGNED
```

之后可能：

```text
另一个参与方 Try 失败
协调器 Cancel
线索回到 NEW
配额释放
```

下游已经看到了最终成功事件，但实际上事务被取消。

所以：

```text
Try 成功 ≠ 全局事务成功
```

标准文档没有区分 ATTEMPT 和 FINAL，这是语义漏洞。

## 4. 三种处理方案

### 方案 A：Try 只发 ATTEMPT 事件

```text
Try -> LEAD_ASSIGNING
Confirm -> LEAD_ASSIGNED
Cancel -> LEAD_ASSIGN_CANCELLED
```

下游必须实现状态机，能处理：

```text
ASSIGNING -> ASSIGNED
ASSIGNING -> CANCELLED
乱序/重复
```

适合确实需要实时展示“处理中”的场景。

### 方案 B：Try 写 PREPARED，Confirm 转 READY

```text
Try:
  业务预占
  + t_local_message(PREPARED)

Confirm:
  业务转正
  + t_local_message(PREPARED -> READY)

Cancel:
  t_local_message(PREPARED -> CANCELLED)

Relay:
  只扫 READY
```

这样消息行提前插入，但 Confirm 才决定可投递。

当前 `t_local_message` 只有：

```text
INIT / SENT / DONE / DEAD
```

没有 `PREPARED`，采用方案 B 需要扩状态。

### 方案 C：终态事件在 Confirm 写

```text
Try:
  只做资源预占 + barrier

Confirm:
  业务最终状态
  + barrier(CONFIRMED)
  + t_local_message(INIT)

Cancel:
  释放资源
  不写成功事件
```

这是最简单、最不容易产生假成功事件的方案。

代价：

```text
下游要等 Confirm 完成后才收到终态事件
```

## 5. 本项目决定

```text
终态业务事件：Confirm 本地事务写 Outbox
Try：只做资源预占和 barrier
Cancel：释放资源，不发成功事件
```

例如：

```text
LEAD_ASSIGNED
CONTRACT_EFFECTIVE
ORDER_PAID
```

这些全部只在 Confirm 后发。

如果确实需要“处理中”展示：

```text
LEAD_ASSIGNING
```

它只能是独立 ATTEMPT 事件，并且必须有：

```text
LEAD_ASSIGN_CANCELLED
```

下游必须实现状态机，不能把 ATTEMPT 当 FINAL。

本项目默认不做 ATTEMPT 事件。
原因：

```text
没有任何核心业务应该依赖一个可能被 Cancel 的通知
```

标准文档 `58CRM_-_系统分布式事务项目_.md` 的 Try 后写本地消息表，是设计缺陷，不照搬。

## 6. 消息服务边界

消息服务不查 TCC 表，也不判断 Try/Confirm。

阶段由生产方决定：

```text
TccAction.tryAction
TccAction.confirmAction
TccAction.cancelAction
```

消息服务只保证：

```text
写进 Outbox 的消息可靠投递
消费端幂等
失败可恢复
```

## 7. 一句话

> **标准文档是在 Try 成功后写消息；这只适合 ATTEMPT 事件。终态事件要么放 Confirm，要么引入 PREPARED/READY，不能直接把 Try 的预占成功当最终成功。**
