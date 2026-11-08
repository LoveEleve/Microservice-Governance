# TCC 待办与风险

## P0：号段分配器不是原子操作

文件：

```text
xhs-demo/id-lab/src/main/java/com/scalab/id/segment/JdbcSegmentAllocator.java
```

当前：

```java
UPDATE id_segment SET max_id = max_id + ? WHERE biz_type = ?;
SELECT max_id FROM id_segment WHERE biz_type = ?;
```

问题：

```text
两条 SQL、两次连接
并发领号可能读到同一个 max_id
两个 TC 拿到重叠号段
XID 重复
```

修复方向：

```sql
UPDATE id_segment
SET max_id = LAST_INSERT_ID(max_id + ?)
WHERE biz_type = ?;

SELECT LAST_INSERT_ID();
```

要求同一 Connection；或使用 `SELECT ... FOR UPDATE` + `UPDATE` 的事务。

必须补测试：

```text
两个 DataSource / 两个 SegmentIdGenerator
并发领号
断言所有号段不重叠
```

在修复前：

```text
不能把该 SegmentIdGenerator 用于 TCC XID
```

## P1：多 DB / secondary 未落地

文档 `10-tinyid-optimizations-and-migration.md` 讨论过多 DB，但代码只有单 DataSource。

第一版先单库，保留 secondary 配置口子即可。

## P1：迁移工具未落地

雪花/自增 -> 号段的迁移步骤只有文档，没有自动化工具。

## P2：真实多 TC 号段并发测试

现有 `JdbcSegmentAllocatorTest` 主要验证单 JdbcTemplate，不能证明 5 个 TC 共享 DB 时号段不重叠。

## 下一步讨论

```text
Coordinator 状态机与表
  t_tcc_global
  t_tcc_branch
```

要定：

```text
字段
状态值
状态迁移
branch_seq
timeout
retry/next_retry
owner_token/lease
callback endpoint 存储
```

一句话：

> **号段模式可用，但当前分配器有 P0 并发 bug；TCC XID 接入前必须先修复原子领号并做多实例测试。**
