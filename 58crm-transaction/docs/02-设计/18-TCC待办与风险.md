# TCC 待办与风险

## P0：号段分配器不是原子操作（已修）

历史来源：

```text
xhs-demo/id-lab/src/main/java/com/scalab/id/segment/JdbcSegmentAllocator.java
```

原实现的问题：

```text
UPDATE 和 SELECT 是两次 JdbcTemplate 调用
可能来自连接池的两个连接
并发领号可能读到同一个 max_id
两个 TC 拿到重叠号段
```

本项目不再直接依赖 id-lab。修复后的实现：

```text
crm-tx-tcc-id
  MybatisSegmentAllocator
  @Transactional(REQUIRES_NEW)
  UPDATE + SELECT 同一连接、同一事务
```

为什么不用 `LAST_INSERT_ID`：

```text
本项目用 MyBatis-Plus + Spring TX
UPDATE 持有行锁，SELECT 同事务读取新水位
不引入 MySQL 特有函数，H2 测试和 MySQL 行为一致
```

测试：

```text
8 线程 × 25 次领号
断言 max_id 返回值不重复
断言 DB 最终水位 = 200 × 100
外层事务回滚，号段仍独立提交
```

注意：

```text
id-lab 里的旧 JdbcTemplate 实现仍然是学习代码
不进入 58crm 运行时
```

## P0：Scheduler 恢复窗口

```text
Commit/Rollback 必须在同一事务把 branch 推到 CONFIRMING/CANCELLING
并设置 next_retry_at=NOW(3)
否则 global 已推进、branch 仍 TRY_SUCCESS
Scheduler 扫不到，事务永久卡住
```

已补设计，代码在 C-003.3 必须按此实现并测试。

## P1：callback 未鉴权

```text
第一版内网可信
无 token/mTLS/allowlist
生产化前必须补
```

## P1：多 DB / secondary 未落地

文档 `10-tinyid-optimizations-and-migration.md` 讨论过多 DB，但代码只有单 DataSource。

第一版先单库，保留 secondary 配置口子即可。

## P1：迁移工具未落地

雪花/自增 -> 号段的迁移步骤只有文档，没有自动化工具。

## P2：真实多 TC 号段并发测试

现有 `JdbcSegmentAllocatorTest` 主要验证单 JdbcTemplate，不能证明 5 个 TC 共享 DB 时号段不重叠。

## 下一步

```text
C-003.2.1 Coordinator 核心
  begin / register / report
  t_tcc_global / t_tcc_branch persistence
  已完成

C-003.2.2 Coordinator gRPC Server
  proto codegen
  service adapter
  error mapping
  下一步
```

一句话：

> **号段模式可用，但当前分配器有 P0 并发 bug；TCC XID 接入前必须先修复原子领号并做多实例测试。**
