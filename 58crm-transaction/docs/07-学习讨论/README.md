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
| `04-2026-11-08-业务方如何使用.md` | E2E 裸协议接入示例（非最终 API，先看 16 号 Review） |

## 3. 学习入口

```text
业务入口：
  crm-tx-tcc-e2e-lead/src/main/java/com/wuba/crm/tx/e2e/lead/LeadController.java

真实调用链：
  LeadController
  -> LeadAssignService
  -> TccCoordinatorClient
  -> QuotaFeignClient
  -> QuotaController
  -> TccParticipantService
```

## 4. 当前结论

```text
TCC 状态机、barrier、空回滚、Confirm + Outbox 同事务
Scheduler 重试、Nacos gRPC 注册、真实 E2E 已跑通
当前服务已停止
```

## 5. 一句话

> **这个文件夹记录“为什么这么设计、代码怎么走、我们讨论出了什么”，不是重复抄设计文档。**
