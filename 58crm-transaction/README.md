# 58crm-transaction

CRM 跨服务一致性框架：TCC + MySQL 本地消息表 + Redis Stream + 对账。

## 先看什么

按使用顺序读，不要先读源码：

```text
1. README.md                              项目是什么、边界是什么
2. docs/README.md                         文档目录
3. docs/01-使用/03-Starter接入手册.md     Spring Boot 业务方怎么用（先看这个）
4. docs/01-使用/02-生产端接入手册.md      手工装配/底层用法
5. docs/00-阅读指南.md                    不同角色怎么读
6. docs/02-设计/01-核心模型.md            TCC / Outbox / Redis / MySQL 的职责
7. docs/03-数据/01-数据模型.md            表、键、状态
8. docs/04-验证/01-测试与验收.md          AC 怎么验证
9. docs/05-代码讲解/00-代码地图.md        源码从哪里开始看
10. docs/05-代码讲解/05-业务示例模块讲解.md
11. docs/05-代码讲解/06-Starter类与方法全解.md
12. docs/06-Redis-Stream/01-原理与使用.md
```

## 一句话模型

```text
MySQL 是正本：业务事实、TCC 日志、Outbox、幂等、对账
Redis Stream 是传送带：PEL + XACK，可恢复
一条事件只进一个业务线 Stream
一个业务线一个消费组，不做 fan-out
```

## 生产形态

```text
MySQL 8.0
ShardingSphere-JDBC：16 库 × 4 表 = 64 分片
1 主 2 从，读写分离
Redis 7.x Cluster（本机验证 7.0.15）：3 主 3 从，Stream 分片
Nacos：注册 + 配置
Sentinel：8W 流控 + 熔断
协调器：无状态 5 实例
Prometheus + Grafana + 链路追踪
```

代码按生产形态写，不提供 `production=false` 的单库分支。  
本地测试可以指向单实例，但必须走同一套逻辑表、分片键和路由代码。

## 当前阶段

```text
C-001.1：OutboxPublisher（已完成）
C-001.2：Scanner + Buffer + Sender + Redis Stream（已完成）
C-002：Redis Stream 消费者（已完成）
C-003.1：TCC API / 状态 / DTO / proto（已完成）
C-003.1.1：TCC 号段 ID（已完成）
C-003.2.1：Coordinator 核心 Begin/Register/Report（已完成）
C-003.2.2：Coordinator gRPC Server（已完成）
C-003.3：Commit / Rollback / RM callback（已完成）
C-003.4：Participant Barrier（已完成）
C-003.5：Confirm 事务契约与 Outbox 示例（已完成）
C-003.6：Scheduler / 重试（已完成）
C-003.7：生产化接入（Starter + Metrics + Nacos + 真实 E2E 已完成）
```

TCC 协调器、参与方 barrier、业务示例在后续变更中实现。

## 工程目录

```text
crm-tx-common        通用模型/状态/错误码
crm-tx-tcc-api       TCC API / 状态 / DTO / proto
crm-tx-tcc-id        TCC XID 号段生成
crm-tx-tcc-coordinator TCC 协调器核心
crm-tx-tcc-participant 参与方 SDK/barrier（后续）
crm-tx-outbox        Outbox/Scanner/Buffer/Sender/Stream
crm-tx-consumer      Redis Stream 消费者
crm-tx-spring-boot-starter  Spring Boot 自动装配
crm-tx-demo      业务方接入示例（线索分配）
```

## 学习入口

```text
docs/07-学习讨论/00-项目学习路线.md
```

## 验证

```text
编译
单元测试
集成测试（单实例 fixture，生产路由规则）
静态检查
```

不要求真实部署 64 个库、Redis Cluster、Nacos/Sentinel 集群。
