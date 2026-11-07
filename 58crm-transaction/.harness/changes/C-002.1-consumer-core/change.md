# C-002.1：消费者核心模型与仓储

## 用户故事

作为消费方服务，我需要把 Redis Stream 消息持久化为可恢复的消费记录，并在进程崩溃后按租约重新领取；业务方只注册 handler，不关心存储和并发。

## 范围

```text
crm-tx-consumer 模块
ConsumeStatus
ConsumedMessage
ConsumerRecord
ClaimRequest
ConsumerRecordEntity / Mapper / Repository
t_consume_record DDL
HandlerRegistry
```

## Out of Scope

```text
StreamPuller
ConsumePersister
ConsumerWorker
AckBatcher
LeaseWheel
PendingRecovery
Spring Boot Starter 装配
```

## 验收条件

```text
AC1 READY 记录批量落库
AC2 重复 msg_id 落库被忽略，不重置已有状态
AC3 claimReadyBatch 只领取 READY 且到达 next_retry_at 的记录
AC4 领取时 owner_epoch/claim_count 递增，写入 owner_token/consumer_id/lease_until
AC5 非 owner/非当前 epoch 的 CONSUMED 更新返回 0
AC6 lease 过期记录可查询
AC7 重试未达上限回到 READY，达到上限进入 DEAD
AC8 HandlerRegistry 能按 bizCode + eventType 精确找到 handler
AC9 重复注册 handler 启动期失败
AC10 未知 handler 抛出明确异常
```

## 边界情况

```text
重复投递
payload 和 payloadRef 同时为空
owner_token 已被其他实例替换
owner_epoch 已过期
lease_until 已过期
next_retry_at 未到达
```

## 非功能需求

```text
所有消费记录 SQL 带 msg_id
claim 按 created_at 顺序
批量上限 1000
MySQL/H2 使用同一套 SQL 语义
时间统一 UTC
```

## 设计约束

```text
MySQL 是消费状态正本
Redis Stream 只负责传输和 PEL
READY 无 owner，不需要续约
PROCESSING 才有 owner_token/owner_epoch/lease
CONSUMED/DEAD 是终态
```

## 契约

```java
public interface MessageHandler {
    void handle(ConsumedMessage message) throws Exception;
}

public interface ConsumerRecordRepository {
    int insertReadyBatch(List<ConsumedMessage> messages, Instant now);
    List<ConsumerRecord> claimReadyBatch(ClaimRequest request);
    int markConsumed(String msgId, String ownerToken, long ownerEpoch, Instant now);
    int markRetry(String msgId, String ownerToken, long ownerEpoch,
                  Instant nextRetryAt, int maxRetries, Instant now);
    int markDead(String msgId, String ownerToken, long ownerEpoch, Instant now);
    List<ConsumerRecord> findExpiredLeases(Instant now, int limit);
}
```

## 测试策略

```text
单元：
  ConsumerRecord 校验
  ClaimRequest 校验
  HandlerRegistry 查找/重复/未知

集成：
  H2 + MyBatis-Plus 真实 SQL
  READY 批量插入
  重复插入
  claim 状态迁移
  fencing 失败
  retry/DEAD
  expired lease
```

## 验收用例

```text
insertReadyBatch_insertsReadyRecords
insertReadyBatch_duplicateMsgId_isIgnored
claimReadyBatch_movesReadyToProcessingWithEpoch
markConsumed_withWrongOwner_doesNotUpdate
markRetry_belowMax_returnsReady
markRetry_atMax_becomesDead
findExpiredLeases_returnsOnlyExpiredProcessing
handlerRegistry_resolvesByBizCodeAndEventType
handlerRegistry_duplicateRegistrationFails
```
