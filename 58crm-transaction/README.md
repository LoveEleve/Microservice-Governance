# 58crm-transaction

CRM 跨服务一致性框架：TCC + MySQL 本地消息表 + Redis List + 对账。

## 先看什么

按使用顺序读，不要先读源码：

```text
1. README.md                              项目是什么、边界是什么
2. docs/README.md                         文档目录
3. docs/00-阅读指南.md                    不同角色怎么读
4. docs/01-使用/01-接入手册.md            业务方怎么接入
5. docs/02-设计/01-核心模型.md            TCC / Outbox / Redis / MySQL 的职责
6. docs/03-数据/01-数据模型.md            表、键、状态
7. docs/04-验证/01-测试与验收.md          AC 怎么验证
8. docs/05-代码讲解/00-代码地图.md        源码从哪里开始看
9. docs/05-代码讲解/C-001.1-OutboxPublisher.md
```

## 一句话模型

```text
MySQL 是正本：业务事实、TCC 日志、Outbox、幂等、对账
Redis 是传送带：业务线 List，可丢、可重放
一条事件只进一个业务线 List
不使用消费者组，不做 fan-out
```

## 生产形态

```text
MySQL 8.0
ShardingSphere-JDBC：16 库 × 4 表 = 64 分片
1 主 2 从，读写分离
Redis 5 Cluster：3 主 3 从，32 队列分片
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
C-001.2：Relay + Redis List + 批量 ACK（已完成）
C-001.3：消费方 Inbox + 幂等（下一步）
```

TCC 协调器、参与方 barrier、业务示例在后续变更中实现。

## 工程目录

```text
tx-common        通用模型/状态/错误码
tx-coordinator   TCC 协调器
tx-participant   参与方 SDK/barrier
tx-outbox        Outbox/Relay/Redis List/消费
tx-demo          线索分配示例
```

## 验证

```text
编译
单元测试
集成测试（单实例 fixture，生产路由规则）
静态检查
```

不要求真实部署 64 个库、Redis Cluster、Nacos/Sentinel 集群。
