# 58crm-transaction 项目规则

## 1. 事实基线

- 生产消息层是 Redis Stream，不使用 Kafka/RocketMQ/Redis List。
- 每个业务线一个 Stream：`tx:stream:{bizCode}`；一个技术消费组：`tx-group-{bizCode}`。
- 消费组只用于 ACK/PEL/offset，不做同一事件多下游 fan-out。
- 一条事件只进一个业务线 Stream；需要多个下游时，由上游拆成多条事件。
- MySQL 是正本；Redis 是可丢、可重放的缓冲。
- TCC 的状态、日志、barrier 只在 MySQL。
- payload 不能只放 Redis；必须在 MySQL 或持久对象存储指针中。

## 2. 生产标准

- 代码默认按生产形态写：
  - ShardingSphere-JDBC，16 库 × 4 表 = 64 分片
  - 分片键 `biz_id` / `msg_id`
  - MySQL 1 主 2 从，读写分离
  - Redis Cluster，Stream/业务线键按 slot 分片
  - Nacos 注册/配置
  - Sentinel 流控/熔断
- 禁止 `production=false` 的单库代码路径。
- 测试可用单实例 fixture，但必须走同一套逻辑表、分片键、路由代码。
- 所有消息/幂等 SQL 必须带分片键，不允许全表扫描。

## 3. 文档写法

- 使用优先：先说“业务方怎么用”，再说“内部怎么实现”。
- 每篇文档开头写“读完你能做什么”。
- 先给一个具体例子，再展开概念。
- 每个结论必须能落到接口、表、状态或测试。
- 文档不写与当前变更无关的未来架构。

## 4. 代码写法

- 按垂直切片实现，不按层一次性铺开。
- 每个切片必须有：
  - 对应 `change.md`
  - 失败测试
  - 最小实现
  - 验收测试
- 核心逻辑测试先行：
  - TCC 状态机
  - 空回滚/悬挂/幂等
  - 消息状态迁移
  - 对账
- 测试命名对应 AC，例如：
  - `businessCommitSuccess_writesOutbox`
  - `businessCommitFailure_writesNoMessage`
  - `duplicateMessage_isIdempotent`

## 5. 变更流程

```text
change.md
  → 失败测试
  → 最小实现
  → 重构
  → 编译 + 单测 + 静态检查
  → 更新文档
```

## 6. 提交纪律

- 只提交本次变更相关文件。
- 不把无关工作区改动一起提交。
- 提交信息写清楚变更编号和用户可见结果。
