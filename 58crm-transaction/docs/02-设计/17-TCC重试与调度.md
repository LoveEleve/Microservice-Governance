# TCC 重试与调度

> 读完你能做什么：区分调用级短重试与持久化恢复重试，知道第一版不引入 Spring Retry/Quartz 的原因。

## 1. 两层重试

### 调用级

```text
一次 Confirm/Cancel 调用内
重试 2~3 次
100ms / 300ms
```

只处理瞬时网络抖动/短暂不可用。

不增加 `retry_count`。

### 持久化恢复

```text
t_tcc_global.next_retry_at
t_tcc_branch.next_retry_at
retry_count
```

Scheduler 扫描到期记录，重新调用 Confirm/Cancel。

## 2. 为什么两层都要

只有调用级：

```text
TC 重启 -> 内存重试状态丢失
```

只有持久化：

```text
每次失败都等 Scheduler -> 延迟高
```

## 3. 第一版实现

不引入 Spring Retry/Quartz/ShedLock：

```text
Spring @Scheduled(fixedDelay = 1s)
  -> 扫描 next_retry_at <= now
  -> 按 status/next_retry_at 索引
  -> owner_token + lease_until 抢占
```

多 TC 实例同时扫描也安全：

```text
UPDATE t_tcc_branch
SET owner_token=?, lease_until=?
WHERE status='CONFIRMING'
  AND next_retry_at <= NOW()
  AND lease_until < NOW()
LIMIT ?
```

## 4. 退避

```text
调用级：100ms / 300ms
持久化：1s / 5s / 30s / 2min / 10min
加随机抖动
```

## 5. 失败分类

```text
网络超时/连接拒绝/RM 暂时不可用 -> 重试
参数非法/状态非法/业务明确拒绝 -> DEAD/人工
```

## 6. 计数

```text
retry_count 只统计持久化恢复次数
调用级短重试不增加 retry_count
```

## 7. 后续可选

```text
Quartz JDBC Clustered
ShedLock
XXL-JOB
JobRunr
```

只有需要 cron 管理/任务历史/Dashboard 时再引入。

## 8. 一句话

> **Spring Retry 只适合调用级短重试；TCC 的最终收敛靠 MySQL 状态 + Scheduler + 行级 lease，第一版不需要额外调度组件。**
