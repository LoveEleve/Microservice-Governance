# C-002.5：PendingRecovery

## 用户故事

作为消费方服务，我需要扫描 Redis PEL 中的 pending 消息，按 MySQL 状态区分“Worker 正在处理”和“Worker 崩溃/ACK 丢失”，分别处理，避免 PEL 永久堆积。

## 范围

```text
PendingRecovery
  XPENDING
  XCLAIM
  DB 状态判断
  PROCESSING 过期 -> READY
  CONSUMED/DEAD -> XACK
  DB 缺失 -> claim + READY
ConsumerBootstrap 接入
```

## Out of Scope

```text
XAUTOCLAIM（当前用 XPENDING + XCLAIM，兼容 Redis 5）
生产端 SENT -> DONE 对账
多实例压力测试
真实 Redis/MySQL 集成测试
```

## 验收条件

```text
AC1 CONSUMED 的 pending 只补 XACK
AC2 DEAD 的 pending 只补 XACK
AC3 PROCESSING + lease 有效不处理
AC4 PROCESSING + lease 过期回 READY
AC5 DB 无记录时 claim 并落 READY
AC6 claim 不到 Stream 记录时清 PEL
AC7 Recovery 是后台任务，不执行 handler
AC8 Bootstrap 启动 Recovery，关闭时先停 Puller
```

## 边界情况

```text
PEL 为空
entryId 已从 Stream 删除
DB 有记录但 Stream 丢失
多个实例同时 Recovery
XCLAIM 竞争
```

## 测试策略

```text
单元：
  状态分支
  XACK
  markExpired
  claim missing
集成：
  C-002.6 真实 Redis/MySQL
```
