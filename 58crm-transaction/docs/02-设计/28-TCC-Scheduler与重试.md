# TCC Scheduler 与重试

> 读完你能做什么：知道 Coordinator 如何扫描超时和到期的 branch，如何退避、抢 owner、最终 DEAD。

## 1. 扫描入口

```text
@Scheduled(fixedDelay = retry-scan-ms, 默认 1s)
  -> TccRetryScheduler.scanOnce
```

关闭：

```yaml
crm.tx.tcc.coordinator.scheduler-enabled: false
```

## 2. 两类扫描

二阶段到期：

```sql
SELECT DISTINCT xid
FROM t_tcc_branch
WHERE status IN ('CONFIRMING','CANCELLING')
  AND next_retry_at <= NOW(3)
ORDER BY xid
LIMIT 100;
```

TRYING 超时：

```sql
SELECT xid
FROM t_tcc_global
WHERE status = 'TRYING'
  AND timeout_at <= NOW(3)
ORDER BY timeout_at
LIMIT 100;
```

## 3. 处理

```text
branch due -> 查 global status
  CONFIRMING -> TccPhase2Coordinator.commit
  CANCELLING -> TccPhase2Coordinator.rollback

TRYING timeout -> TccPhase2Coordinator.rollback
```

每个 xid 仍要抢 owner：

```text
TCC_BUSY -> 本轮跳过
其他异常 -> 记录日志，不中断整轮扫描
```

## 4. 退避

```text
1s / 5s / 30s / 2min / 10min
```

第 N 次持久化重试：

```text
retry_count = N
next_retry_at = now + backoff[N]
```

达到上限：

```text
branch DEAD
global FAILED
ERROR 日志
```

## 5. 和调用级重试的区别

```text
调用级：RM 单次 callback 内短重试，不增加 retry_count
持久化：Scheduler 扫描，增加 retry_count，写 next_retry_at
```

## 6. 当前不包含

```text
Nacos 注册/配置接入
Sentinel 限流
Micrometer metrics
真实多实例压测
```

这些属于生产化接入，不在 C-003.6 本地切片。

## 7. 一句话

> **Scheduler 只负责发现到期工作和抢 owner；真正的状态推进与 RM 回调仍然复用 TccPhase2Coordinator。**
