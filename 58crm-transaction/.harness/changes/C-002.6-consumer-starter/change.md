# C-002.6：eventKey 幂等与 Consumer Starter

## 用户故事

作为消费方，我需要用业务事件唯一键 eventKey 做幂等，而不是只靠 msgId；同时业务方只加依赖和配置 `crm.tx.consumer.*` 就能启动消费端。

## 范围

```text
event_key
  Stream 字段解析
  ConsumedMessage / ConsumerRecord
  t_consume_record.event_key
  t_idempotent.biz_key = eventKey
  Worker 幂等入口

consumer starter
  crm.tx.consumer.* 配置
  Mapper / Repository / HandlerRegistry / TransactionOperations
  ConsumerBootstrapManager
  AutoConfiguration
```

## 验收条件

```text
AC1 eventKey 从 Stream 字段进入 ConsumedMessage
AC2 t_consume_record 保存 event_key
AC3 Worker 用 eventKey 作为 t_idempotent.biz_key
AC4 eventKey 为空时回退 msgId
AC5 同一 eventKey 不同 msgId 不重复执行
AC6 Starter 根据 bizCodes 创建多个 ConsumerBootstrap
AC7 Starter 缺少 Mapper/Redis/TransactionManager 时不装配消费端
AC8 crm.tx.consumer.enabled=false 关闭消费端
```

## Out of Scope

```text
对账 SENT -> DONE
死信管理台
真实集群
```
