# 学习讨论

> 目的：记录 58crm-transaction 的学习路线、教学记录、讨论结论和待验证问题。

## 1. 记录规则

每次学习/讨论新增一个文件：

```text
YYYY-MM-DD-主题.md
```

文件至少包含：

```text
今天要解决的问题
先看的业务例子
涉及的设计/源码
讨论结论
还没解决的问题
下一次从哪里继续
```

教学记录必须遵守：

```text
先给具体例子，再讲原理
结论一句话说清
代码要贴完整片段，不只给路径
区分“设计口径”和“当前实现”
```

## 2. 当前文件

| 文件 | 内容 |
|---|---|
| `00-项目学习路线.md` | 从 E2E 入口到 TCC 源码的阅读顺序 |
| `01-2026-11-08-TCC实现与E2E回顾.md` | 已完成 TCC 项目、关键决策、E2E 结果 |
| `02-教学记录模板.md` | 后续每次教学的固定结构 |
| `03-问题清单.md` | 学习过程中待回答/待验证的问题 |
| `04-2026-11-08-业务方如何使用.md` | 历史接入说明，最终 API 见 QUICKSTART |
| `05-TCC吃透计划.md` | 从 lead 入口到面试的学习路线 |
| `06-简历项目描述-TCC与消息.md` | TCC + 消息组件的简历素材 |
| `07-2026-11-08-双RM-TCC逐段讲解.md` | 双 RM（quota/coupon）逐段代码讲解 |
| `08-2026-11-08-切面begin与上下文讨论.md` | 切面 begin、嵌套事务、TccContext、begin 重试的讨论记录 |
| `09-2026-11-08-分支注册职责讨论.md` | TM register 与 RM self-register 的职责讨论 |
| `10-2026-11-08-branchId作用域讨论.md` | branchId 是 xid 内编号，不是全局 ID |
| `11-2026-11-08-Barrier三防.md` | barrier 的空回滚、悬挂、幂等与 Confirm+Outbox |
| `../08-面试思维导图/58CRM-分布式事务与TCC.md` | 分布式事务理论 + 58CRM 项目落地总导图 |
| `../08-面试思维导图/58CRM-消息服务.md` | Outbox/Stream/消费组/PEL/租约/幂等专项导图 |

## 3. 学习入口

```text
业务入口：
  crm-tx-tcc-examples/lead/src/main/java/com/wuba/crm/tx/tcc/example/lead/LeadController.java

真实调用链：
  LeadController
  -> LeadAssignService（TM）
  -> QuotaBranchGateway / CouponBranchGateway
  -> OpenFeign /tcc/try
  -> QuotaTccAction / CouponTccAction（RM）
  -> TccParticipantService（barrier + Confirm/Cancel）
```

## 4. 当前结论

```text
TCC 状态机、barrier、空回滚、Confirm + Outbox 同事务
Scheduler 重试、Nacos gRPC 注册、真实 E2E 已跑通
当前服务已停止
```

## 5. 一句话

> **这个文件夹记录“为什么这么设计、代码怎么走、我们讨论出了什么”，不是重复抄设计文档。**
