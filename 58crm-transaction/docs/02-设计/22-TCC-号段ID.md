# TCC 号段 ID

> 读完你能做什么：知道 TCC XID 为什么用号段、项目内的实现放在哪里，以及为什么不能沿用 id-lab 的 JdbcTemplate 写法。

## 1. 结论

```text
TCC XID 用项目内的 crm-tx-tcc-id
不依赖 xhs-demo/id-lab
持久化用 MyBatis-Plus
数据库记录用独立业务 key tcc-xid
```

## 2. 为什么内联

id-lab 是学习工程，代码服务于 ID 原理演示；58CRM 是实际项目，不能把学习工程的类直接当运行时依赖。

内联后：

```text
包名独立
依赖由本项目管理
表结构、事务、测试由 TCC 自己负责
不再引用 com.scalab.id
```

## 3. 模块结构

```text
crm-tx-tcc-id
  SegmentAllocator
  MybatisSegmentAllocator
  InMemorySegmentAllocator
  SegmentIdGenerator
  SegmentBuffer
  Segment
  SegmentWatermark
  TccXidGenerator
  IdSegmentEntity
  IdSegmentMapper
```

依赖方向：

```text
crm-tx-tcc-id
  -> MyBatis-Plus
  -> Spring TX

crm-tx-tcc-coordinator
  -> crm-tx-tcc-id
```

## 4. 表

```sql
CREATE TABLE IF NOT EXISTS id_segment (
  biz_type VARCHAR(64) PRIMARY KEY COMMENT '业务 key，例如 tcc-xid',
  max_id   BIGINT NOT NULL COMMENT '已分配出去的最大 ID',
  step     INT NOT NULL COMMENT '每次领取数量，也是自适应步长下限'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO id_segment (biz_type, max_id, step)
VALUES ('tcc-xid', 0, 1000)
ON DUPLICATE KEY UPDATE biz_type = biz_type;
```

`max_id` 表示已经分配出去的右边界，实际号段区间是：

```text
[max_id - step, max_id)
```

## 5. 原子领号

第一步：

```sql
UPDATE id_segment
SET max_id = max_id + #{step}
WHERE biz_type = #{bizType}
```

第二步：

```sql
SELECT max_id
FROM id_segment
WHERE biz_type = #{bizType}
```

两步必须在同一个事务、同一个连接里。

实现：

```text
MybatisSegmentAllocator.allocateMaxId
  @Transactional(propagation = REQUIRES_NEW)
  -> mapper.increaseMaxId
  -> mapper.selectMaxId
```

注意：`MybatisSegmentAllocator` 必须由 Spring 容器创建。直接 `new` 会绕过代理，`@Transactional` 不生效。

为什么用 `REQUIRES_NEW`：

```text
号段领用是独立资源分配
不能被外层业务事务回滚
否则业务回滚后 ID 水位也回滚，其他实例可能复用
```

为什么不能直接用 `JdbcTemplate` 的两次调用：

```text
JdbcTemplate 两次调用可能从连接池拿两个连接
第一条 UPDATE 提交后，第二条 SELECT 可能看到别人的新水位
两个 TC 可能拿到同一段
```

## 6. 并发正确性

同一 `biz_type` 的 `UPDATE` 会持有行锁：

```text
tc-0 UPDATE tcc-xid -> 锁行
tc-1 UPDATE tcc-xid -> 等待
tc-0 SELECT max_id -> 读到自己这次更新后的值
tc-0 COMMIT -> 释放锁
tc-1 UPDATE -> 看到新水位
```

因此并发实例的号段不重叠。

测试覆盖：

```text
8 个线程 × 25 次领号
断言 max_id 返回值不重复
断言 DB 最终水位 = 线程数 × 次数 × step
```

## 7. 发号器

`SegmentIdGenerator`：

```text
双 buffer
后台预取
三档步长
```

它从数据库领取一段，后续 XID 从内存 `AtomicLong` 取，只有快用完才预取下一段。

`TccXidGenerator`：

```text
bizType = tcc-xid
nextXid()
负返回码转 XidGenerationException
跳过号段起始的 0，保证 XID > 0
```

## 8. 配置建议

```text
defaultStep = 1000
maxStep = 1_000_000
segmentDuration = 15min
prefetchThreshold = 0.9
```

如果 TC 实例更多、XID 峰值更高，优先提高 `defaultStep`，不要每次发号都访问数据库。

## 9. 运维

监控：

```text
SegmentWatermark
  value
  max
  remaining
  nextReady
```

异常：

```text
id_segment 行不存在 -> 分配失败，不能静默生成
预取失败 -> 当前号段耗尽后返回背压，调用方重试
```

## 10. 一句话

> **号段 ID 的原子性不在 `SELECT`，而在同一事务内先 UPDATE 占住水位；TCC 项目内用 MyBatis-Plus 实现，不直接复用 id-lab。**
