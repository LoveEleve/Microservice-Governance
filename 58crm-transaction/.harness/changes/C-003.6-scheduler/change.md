# C-003.6：Scheduler 与重试

## 用户故事

作为 Coordinator，我可以自动发现停滞的 CONFIRMING/CANCELLING 分支和 TRYING 超时，并继续推进或回滚。

## 范围

```text
TccRetryScheduler
branch due scan
global timeout scan
retry_count / next_retry_at
backoff / DEAD
owner lease 接管
```

## 不包含

```text
Nacos 接入
Sentinel
Micrometer
真实多实例压测
```

## 验收

```text
AC1 due CONFIRMING 自动重试成功
AC2 TRYING 超时自动 rollback
AC3 retryable 写 retry_count/next_retry_at
AC4 达到上限 DEAD/FAILED
AC5 owner lease 有效时跳过
```

## 测试

```text
scheduler_retriesDueConfirm
scheduler_rollsBackExpiredTrying
```

## 完成记录

```text
mvn -q test
tests=200 failures=0 errors=0 skipped=1
```
