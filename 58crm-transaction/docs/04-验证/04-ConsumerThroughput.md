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
| 多消费者运行时（4 × 8） | 10,000 | 32 | 32.2s | 311 | 5 |
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

结果：

```text
10,000 条
32.2 秒
311 QPS
5 次死锁
```

多消费者没有提升，反而下降。  
说明增加消费进程不能绕过单 MySQL 写事务瓶颈。

## 6. 10W QPS 需要什么

```text
64 分片 MySQL
每实例多 Worker
多消费实例共享 consumer group
按分片/业务线拆 claim
降低每消息事务成本
```

按单 MySQL 约 700 QPS 估算：

```text
64 分片 × 700 = 44,800 QPS
需要约 2~3 组等价集群才能接近 100,000 QPS
```

这是容量估算，不是实测结果。

## 7. 运行命令

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
