# 文档目录

> 先按目的选入口，不要从目录第一层顺序读到底。  
> **业务方先看：`01-使用/02-生产端接入手册.md`。**

## 先看业务使用

```text
第一次接入：
  01-使用/02-生产端接入手册.md
→ 看完再读类/方法细节
→ 最后看设计和边界
```

## 我要做什么

| 目的 | 文档 |
|---|---|
| 第一次了解项目 | `00-阅读指南.md` |
| 业务方接入 | `01-使用/01-接入手册.md` |
| Spring Boot Starter 接入 | `01-使用/03-Starter接入手册.md` |
| 生产端手工接入 | `01-使用/02-生产端接入手册.md` |
| 业务示例模块 | `05-代码讲解/05-业务示例模块讲解.md` |
| Starter 类与方法 | `05-代码讲解/06-Starter类与方法全解.md` |
| 理解 TCC / Outbox / Redis / MySQL | `02-设计/01-核心模型.md` |
| 看文档需求映射与差距 | `02-设计/02-需求映射与差距.md` |
| 看 Redis Stream 决策 | `02-设计/03-Redis-Stream-决策.md` |
| 看包结构 | `02-设计/05-包结构.md` |
| 看 bizCode 白名单 | `02-设计/07-BizCodeRegistry.md` |
| 看生产端设计取舍 | `02-设计/08-生产端设计决策与已知边界.md` |
| 看讨论决策日志 | `02-设计/09-讨论决策日志.md` |
| 学 Redis Stream 原理 | `06-Redis-Stream/01-原理与使用.md` |
| Redis Stream 命令大全 | `06-Redis-Stream/02-命令大全.md` |
| 看表和分片 | `03-数据/01-数据模型.md` |
| 看 AC 和测试 | `04-验证/01-测试与验收.md` |
| 看 C-001.2 Review | `04-验证/02-C001.2-Review.md` |
| 看生产端 Review | `04-验证/03-ProducerReview.md` |
| 读代码 | `05-代码讲解/00-代码地图.md` |
| 生产端类与方法全解 | `05-代码讲解/01-生产端类与方法全解.md` |
| 生产端运行时 | `05-代码讲解/02-生产端运行时与线程模型.md` |
| 生产者全链路图 | `05-代码讲解/07-生产者全链路图.md` |
| 消费者模型 | `02-设计/10-消费者模型.md` |
| 消费者实现规划 | `02-设计/11-消费者实现规划.md` |
| 消费者租约与时间轮 | `02-设计/12-消费者租约与时间轮.md` |
| 配置/持久化逐行讲解 | `05-代码讲解/03-配置与持久化逐行讲解.md` |
| 发送管线逐行讲解 | `05-代码讲解/04-发送管线逐行讲解.md` |
| 看 C-001.1 代码讲解 | `05-代码讲解/C-001.1-OutboxPublisher.md` |
| 看 C-001.2 代码讲解 | `05-代码讲解/C-001.2-StreamSender.md` |
| 看 C-002.1 变更范围 | `.harness/changes/C-002.1-consumer-core/change.md` |
| 看 C-002.2 变更范围 | `.harness/changes/C-002.2-consumer-pull-persist/change.md` |
| 看 C-002.3 变更范围 | `.harness/changes/C-002.3-consumer-worker/change.md` |
| 看 C-002.4 变更范围 | `.harness/changes/C-002.4-consumer-bootstrap/change.md` |
| 看 C-002.5 变更范围 | `.harness/changes/C-002.5-pending-recovery/change.md` |
| 看 C-002.6 变更范围 | `.harness/changes/C-002.6-consumer-starter/change.md` |
| 看消费端吞吐压测 | `04-验证/04-ConsumerThroughput.md` |
| 看 C-001 变更范围 | `.harness/changes/C-001-outbox-redis-list/change.md` |
| 看 TCC 与消息联动 | `02-设计/13-TCC与消息联动.md` |
| 看 TCC 框架分析 | `02-设计/14-TCC框架分析.md` |
| 看 TCC 服务器架构 | `02-设计/15-TCC服务器架构.md` |
| 看 TCC Barrier | `02-设计/16-TCC-Barrier与三防.md` |
| 看 TCC 重试与调度 | `02-设计/17-TCC重试与调度.md` |
| 看 TCC 待办与风险 | `02-设计/18-TCC待办与风险.md` |
| 看 TCC 时序与状态图 | `02-设计/19-TCC时序与状态图.md` |
| 看 TCC API 与 Proto | `02-设计/20-TCC-API与Proto.md` |
| 看 TCC 表结构与 SQL | `02-设计/21-TCC表结构与SQL.md` |
| 看 TCC 决策日志 | `02-设计/09-讨论决策日志.md` |
| 看 TCC 号段 ID | `02-设计/22-TCC-号段ID.md` |
| 看 TCC Coordinator 核心 | `02-设计/23-TCC-Coordinator核心.md` |
| 看 C-003 变更范围 | `.harness/changes/C-003-tcc-core/change.md` |
| 看 C-003.1 Review | `04-验证/05-C003.1-Review.md` |
| 看 C-003.2.1 Review | `04-验证/06-C003.2.1-Review.md` |
| 看 TCC gRPC 与启动 | `02-设计/24-TCC-gRPC与启动.md` |
| 看 C-003.2.2 Review | `04-验证/07-C003.2.2-Review.md` |
| 看 TCC 二阶段与 Callback | `02-设计/25-TCC二阶段与Callback.md` |
| 看 C-003.3 Review | `04-验证/08-C003.3-Review.md` |
| 看 TCC Participant Barrier | `02-设计/26-TCC-Participant-Barrier.md` |
| 看 C-003.4 Review | `04-验证/09-C003.4-Review.md` |
| 看 Confirm-Outbox 边界 | `02-设计/27-TCC-Confirm-Outbox边界.md` |
| 看 C-003.5 Review | `04-验证/10-C003.5-Review.md` |
| 看 TCC Scheduler 与重试 | `02-设计/28-TCC-Scheduler与重试.md` |
| 看 C-003.6 Review | `04-验证/11-C003.6-Review.md` |
| 看 TCC Participant Starter | `02-设计/29-TCC-Production-Starter.md` |
| 看 C-003.7 Starter Review | `04-验证/12-C003.7-Starter-Review.md` |
| 看 TCC Metrics | `02-设计/30-TCC-Metrics.md` |
| 看 C-003.7 Metrics Review | `04-验证/13-C003.7-Metrics-Review.md` |
| 看 TCC Nacos 接入 | `02-设计/31-TCC-Nacos接入.md` |
| 看 C-003.7 Nacos Review | `04-验证/14-C003.7-Nacos-Review.md` |
| 看 C-003.7 真实 E2E | `04-验证/15-C003.7-E2E.md` |
| 开始学习本项目 | `07-学习讨论/00-项目学习路线.md` |
| 看框架接入层 Review | `04-验证/16-Framework-Access-Review.md` |
| 看 TCC 公开 API 重构规划 | `02-设计/33-TCC公开API与SDK重构规划.md` |
| 看 C-004 规划 Review | `04-验证/17-C004-Plan-Review.md` |
| 看 C-004.1 Review | `04-验证/18-C004.1-Review.md` |
| 看 C-004.1 深度 Review | `04-验证/19-C004.1-Deep-Review.md` |
| 看学习讨论记录 | `07-学习讨论/README.md` |

## 目录结构

```text
docs/
├── README.md
├── 00-阅读指南.md
├── 01-使用/
│   ├── 01-接入手册.md
│   ├── 02-生产端接入手册.md
│   └── 03-Starter接入手册.md
├── 02-设计/
│   ├── 01-核心模型.md
│   ├── 02-需求映射与差距.md
│   ├── 03-Redis-Stream-决策.md
│   ├── 05-包结构.md
│   ├── 07-BizCodeRegistry.md
│   ├── 08-生产端设计决策与已知边界.md
│   ├── 09-讨论决策日志.md
│   ├── 10-消费者模型.md
│   ├── 11-消费者实现规划.md
│   └── 12-消费者租约与时间轮.md
├── 03-数据/
│   └── 01-数据模型.md
├── 04-验证/
│   ├── 01-测试与验收.md
│   ├── 02-C001.2-Review.md
│   └── 03-ProducerReview.md
├── 05-代码讲解/
│   ├── 00-代码地图.md
│   ├── 01-生产端类与方法全解.md
│   ├── 02-生产端运行时与线程模型.md
│   ├── 03-配置与持久化逐行讲解.md
│   ├── 04-发送管线逐行讲解.md
│   ├── 05-业务示例模块讲解.md
│   ├── 06-Starter类与方法全解.md
│   ├── 07-生产者全链路图.md
│   ├── C-001.1-OutboxPublisher.md
│   └── C-001.2-StreamSender.md
└── 06-Redis-Stream/
    ├── 01-原理与使用.md
    └── 02-命令大全.md
```

## 写作约定

```text
使用文档：只讲怎么用，不展开内部类
设计文档：讲职责和边界，不贴大段代码
数据文档：讲表、键、状态、分片
验证文档：讲 AC、测试用例、运行命令
代码讲解：按调用链讲具体类
变更文档：只讲本次范围和验收
```
