# 消费端吞吐压测（2026-11-07）

> 结论先行：**单机单 MySQL 没有达到 10W QPS，实测约 715 QPS。**
> Redis 不是瓶颈，瓶颈是每条消息一个 MySQL 事务。

## 1. 环境

```text
CPU       16 core
Memory    15 GiB
Redis     7.0.15，127.0.0.1:6379
MySQL     8.0，容器 mysql-p9，127.0.0.1:3306
连接池    HikariCP
测试数据  100,000 条
```

压测时临时调整 MySQL：

```text
innodb_flush_log_at_trx_commit = 2
sync_binlog = 0
innodb_buffer_pool_size = 1 GiB
```

这是本机压测参数，不代表生产配置。

## 2. 测试链路

```text
Redis XADD pipeline
  -> XREADGROUP
  -> Queue
  -> Persister INSERT READY
  -> Worker claim
  -> t_idempotent INSERT
  -> handler
  -> mark CONSUMED
  -> AckBatcher XACK
```

handler 为空，仍然执行：

```text
t_idempotent INSERT
t_consume_record 状态更新
每消息一个本地事务
```

## 3. 结果

| 场景 | 数量 | Worker | 耗时 | QPS | 死锁 |
|---|---:|---:|---:|---:|---:|
| 单 Worker | 10,000 | 1 | 29.8s | 335 | - |
| 多 Worker | 10,000 | 16 | 16.2s | 617 | 0 |
| 多 Worker | 10,000 | 64 | 27.1s | 369 | 0 |
| 多消费者运行时（4 × 8，修复后） | 10,000 | 32 | 18.4s | 542 | 0 |
| 多 Stream（4 Stream × 2 实例 × 8 Worker） | 10,000 | 64 | 26.7s | 375 | 0 |
| 多进程（4 Stream × 1 JVM × 8 Worker） | 10,000 | 32 | 14.2s | 702 | 未统计 |
| 目标规模 | 100,000 | 16 | 139.8s | 715 | 0 |

## 4. 结论

```text
Redis 拉取/队列/ACK 不是当前瓶颈
MySQL 每消息事务是瓶颈
Worker 线程从 16 加到 64 反而下降
多消费者运行时 4 × 8 也下降，说明单 MySQL 连接/锁竞争已经饱和
```

单机不能支持 10W QPS。

## 5. 多消费者实测

本次压测不是只测一个 `ConsumerBootstrap`，也跑了：

```text
4 个 ConsumerBootstrap
每个 8 Worker
共享同一 Stream / group / MySQL
```

修复后结果：

```text
10,000 条
18.4 秒
542 QPS
0 次死锁
attempts=10000
```

多消费者没有超过单实例 16 Worker，说明增加消费线程不能绕过单 MySQL 写事务瓶颈。

## 6. 死锁复盘

### 6.1 现象

多消费者首次压测：

```text
10,000 条
32.2 秒
311 QPS
5 次死锁
attempts=10001
```

`attempts=10001` 说明死锁回滚后有消息被重新执行。

### 6.2 InnoDB 现场

死锁发生在：

```text
ConsumePersister: INSERT IGNORE t_consume_record
ConsumerWorker:  UPDATE t_consume_record SET status='CONSUMED'
```

Persister 的批量插入包含已经存在的 `msg_id`，例如：

```text
load-27 已经是 PROCESSING
INSERT IGNORE 仍然要对已存在行做唯一键检查
-> Persister 持有 S 锁

Worker 对同一行 markConsumed
-> 等待 X 锁
```

形成：

```text
Persister S 锁
       ↓
Worker X 锁等待
       ↓
死锁
```

根因不是 Redis，也不是 Stream，而是：

```text
Persister 对已存在消息重复 INSERT
```

### 6.3 修复

```text
insertReadyBatch
  -> SELECT msg_id 已存在记录
  -> 过滤已存在 ID
  -> 只 INSERT 真正的新消息
```

同时把 claim 从多行 `UPDATE ... IN` 改为：

```text
先选候选 ID
-> 单行条件 UPDATE
-> 避免多行锁顺序死锁
```

修复后：

```text
4 个消费运行时
10,000 条
18.4 秒
542 QPS
0 死锁
attempts=10000
```

### 6.4 相关提交

```text
98dfa19 fix(tx): avoid duplicate inbox insert lock deadlocks
```

## 7. 10W QPS 的后续方向

当前不先上分片。优先验证：

```text
批量幂等/状态写入
多消费者 + 多 Stream（已跑通）
真实业务 handler 成本
MySQL 连接池和锁竞争
```

如果优化后仍不够，再评估：

```text
分片
多库
按分片领取
```

原则：

```text
先证明单库优化上限
再决定是否分片
不在没有基准的情况下先改分片
```

## 8. 运行命令

```bash
mvn -pl crm-tx-consumer -am test \
  -Dtx.it=true \
  -Dtx.mysql.password=root123 \
  -Dtx.load.count=100000 \
  -Dtx.load.xadd.batch=500 \
  -Dtx.load.workers=16 \
  -Dtx.load.pool=16 \
  -Dtest=ConsumerThroughputIT \
  -Dsurefire.failIfNoSpecifiedTests=false
```
