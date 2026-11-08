# C-003：TCC 核心

## 用户故事

作为跨服务业务方，我需要 Try 预占资源、Confirm 提交、Cancel 释放，并在 Confirm 成功后可靠通知下游。

## 范围

```text
C-003.1
  TCC API / 状态 / DTO / proto
  控制流协议

C-003.2
  Coordinator Server
  begin / register / report
  t_tcc_global / t_tcc_branch

C-003.3
  commit / rollback
  RM 控制流下发 Confirm/Cancel
  CAS + 幂等

C-003.4
  Participant barrier
  空回滚 / 悬挂 / Confirm-Cancel 幂等

C-003.5
  Confirm 同事务写 Outbox
  终态消息

C-003.6
  Scheduler / lease / retry
  Nacos 只发现 TC
  Sentinel / metrics / multi-instance
```

## 明确不做

```text
TC 发现 RM
TC 配置业务方 serviceName
callback token/mTLS/allowlist（第一版）
管理台/告警平台（第一版）
把 TRY 写进 t_local_message
Try 阶段发终态事件
把 TCC 状态放 Redis
```

## 验收

```text
AC1 TM -> TC unary
AC2 TC -> RM unary callback，endpoint 来自 branch register
AC3 TC 不发现 RM，不依赖业务方 serviceName
AC4 Try/Confirm/Cancel 幂等
AC5 空回滚/悬挂
AC6 Confirm 写 Outbox
AC7 多实例 global owner lease
AC8 崩溃恢复
```

## 测试

```text
单元：状态机、barrier、幂等
集成：MySQL + gRPC + 控制流
故障：断连、RM 重启、重复 Confirm/Cancel
```


## C-003.1 完成记录

```text
crm-tx-tcc-api 模块
GlobalStatus / BranchStatus / BarrierStatus
TccResultCode / TccPhaseResult / TccTryResult
TccException
BusinessActionContext / TccAction / TccTransactionTemplate
Coordinator DTO
Participant callback DTO
src/main/proto/crm/tx/tcc/v1/tcc.proto
```

验证：

```text
mvn -q test
tests=119 failures=0 errors=0 skipped=1
```

说明：

```text
proto 源文件已建立
gRPC codegen 和 Server 放到 C-003.2
```


## C-003.1.1 号段 ID 内联

```text
新增 crm-tx-tcc-id
MyBatis-Plus IdSegmentMapper / MybatisSegmentAllocator
@Transactional(REQUIRES_NEW)
TccXidGenerator 跳过 0，保证 XID > 0
```

验证：

```text
mvn -q test
tests=133 failures=0 errors=0 skipped=1
```
