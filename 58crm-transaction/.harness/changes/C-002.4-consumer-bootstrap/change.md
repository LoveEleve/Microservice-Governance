# C-002.4：消费方幂等与 Bootstrap

## 用户故事

作为消费方服务，我需要把 Worker、Puller、Persister、ACK、租约装配成可启动的消费运行时，并在业务处理前用 `t_idempotent` 拦截同一消息的重复执行。

## 范围

```text
C-002.4a 幂等
  IdempotentRecordEntity
  IdempotentMapper
  IdempotentRepository
  MybatisIdempotentRepository
  ConsumerWorker 接入

C-002.4b Bootstrap
  Redis group 初始化
  ConsumerRuntimeConfig
  ConsumerBootstrap
  启动/关闭顺序
```

## Out of Scope

```text
PendingRecovery / XAUTOCLAIM
多实例集成测试
生产端 SENT -> DONE 对账
TCC
```

## 验收条件

```text
AC1 同一 msgId 首次处理插入 t_idempotent
AC2 同一 msgId 重复处理不再调用 handler
AC3 幂等插入、业务 handler、CONSUMED 同一事务
AC4 handler 失败回滚幂等记录
AC5 重复消息可补 XACK
AC6 Bootstrap 按 stream/group 创建 group
AC7 Bootstrap 启动顺序 Puller 最后
AC8 Bootstrap 关闭顺序 Puller 最先
AC9 多 bizCode 各自独立 queue/worker/group
```

## 边界情况

```text
Redis group BUSYGROUP
stream 不存在
幂等记录存在但 CONSUMED 失败
重复投递
Bootstrap 部分启动失败
关闭时队列非空
```

## 测试策略

```text
单元：
  IdempotentRepository H2
  ConsumerWorker 幂等命中
  Bootstrap 启动/关闭顺序

集成：
  真实 Redis/MySQL 留到 C-002.5
```
