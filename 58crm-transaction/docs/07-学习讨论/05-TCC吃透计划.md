# TCC 吃透计划

> 目标：不是“读过源码”，而是能讲清、能改、能排障、能应对面试和简历追问。

## 1. 吃透标准

```text
L1 能画出成功/失败链路
L2 能解释每个状态、表和 owner fence
L3 能新增一个 TccAction 并接入真实服务
L4 能故障注入并解释修复路径
L5 能用 1 分钟讲清项目取舍
```

## 2. 学习顺序

### 阶段 1：使用视角，从 lead 开始

读：

```text
QUICKSTART.md
crm-tx-tcc-examples/lead/.../LeadController.java
crm-tx-tcc-examples/lead/.../LeadAssignService.java
crm-tx-tcc-examples/lead/.../QuotaFeignClient.java
crm-tx-tcc-examples/lead/.../CouponBranchGateway.java
crm-tx-tcc-examples/lead/.../CouponFeignClient.java
crm-tx-tcc-api/.../TccRequest.java
crm-tx-tcc-api/.../TccBranch.java
```

要能回答：

```text
requestId 为什么是幂等键
bizCode/bizId 用来干什么
Try 为什么是业务 API，不是 TC 调用
OpenFeign 和 gRPC callback 分别在哪里
Try 失败后模板做了什么
```

产出：

```text
手画成功链路（quota + coupon 两个 RM）
手画失败链路（quota 成功、coupon 失败、quota 被 Cancel）
```

### 阶段 2：参与方 RM

读：

```text
crm-tx-tcc-examples/quota/.../QuotaTccAction.java
crm-tx-tcc-examples/coupon/.../CouponTccAction.java
crm-tx-tcc-participant/.../TccParticipantService.java
crm-tx-tcc-participant/.../web/TccParticipantTryController.java
docs/02-设计/16-TCC-Barrier与三防.md
```

要能回答：

```text
Try/Confirm/Cancel 各写什么
barrier TRIED/CONFIRMED/CANCELLED/SUSPENDED 怎么变
空回滚、悬挂怎么发生
为什么 Confirm 里要写 Outbox
```

产出：

```text
新增一个 Action 或修改 quota 的 Try 条件
```

### 阶段 3：客户端与 Coordinator

读：

```text
crm-tx-tcc-client/.../DefaultTccTransactionTemplate.java
crm-tx-tcc-client/.../CoordinatorGrpcClient.java
crm-tx-tcc-coordinator/.../TccCoordinatorService.java
crm-tx-tcc-coordinator/.../TccPhase2Coordinator.java
docs/02-设计/19-TCC时序与状态图.md
docs/02-设计/21-TCC表结构与SQL.md
```

要能回答：

```text
Begin/Register/Report/Commit 各自做什么
global/branch 状态怎么转
owner lease 为什么必须有
commit 和 rollback 为什么在 DB 事务外回调
```

产出：

```text
状态迁移表
owner fence SQL 解释
```

### 阶段 4：可靠性

读：

```text
docs/02-设计/28-TCC-Scheduler与重试.md
crm-tx-tcc-coordinator/.../TccRetryScheduler.java
crm-tx-tcc-coordinator/.../TccRetryPolicy.java
docs/04-验证/11-C003.6-Review.md
```

要能回答：

```text
retryable 后谁重试
next_retry_at 和 retry_count 怎么变
为什么 owner lease 有效时 Scheduler 抢不到
DEAD/FAILED 怎么产生
```

产出：

```text
故障注入：停 quota、停 TC、重复 Confirm
```

### 阶段 5：生产化与 E2E

读：

```text
docs/02-设计/29-TCC-Production-Starter.md
docs/02-设计/30-TCC-Metrics.md
docs/02-设计/31-TCC-Nacos接入.md
docs/04-验证/15-C003.7-E2E.md
docs/04-验证/24-C004.6-Review.md
```

要能回答：

```text
Nacos 为什么只注册 TC，不发现 RM
client/participant 为什么要分开开关
Callback endpoint 为什么不能用 serviceName
metrics 为什么不影响主流程
```

产出：

```text
真实启动三个服务复跑成功/失败
```

### 阶段 6：面试与简历

要能 1 分钟讲：

```text
项目解决什么问题
TCC 与消息组件怎么分工
最关键的设计取舍
遇到并修复的故障
```

## 3. 每日节奏

```text
30 分钟读代码
30 分钟画图/写笔记
30 分钟跑一个实验
15 分钟写 3 个面试问答
```

## 4. 每次记录

```text
docs/07-学习讨论/YYYY-MM-DD-主题.md
```

固定内容：

```text
今天学了什么
一个具体例子
源码调用链
一个没解决的问题
下一次从哪继续
```

## 5. 不要做的事

```text
不要从 Coordinator 私有方法开始
不要背类名
不要只看正文不跑实验
不要把 E2E 裸协议当最终业务 API
```
