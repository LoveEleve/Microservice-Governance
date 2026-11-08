# C-003.4：Participant Barrier

## 用户故事

作为 RM，我可以在本地事务中执行 Try/Confirm/Cancel，并通过 barrier 防止幂等、空回滚和悬挂。

## 范围

```text
crm-tx-tcc-participant
  t_branch_barrier
  BarrierRepository
  TccParticipantService
  TccActionRegistry
  TccParticipantCallbackGrpcService
  TccParticipantGrpcServer
```

## 不包含

```text
Confirm 同事务写 Outbox
participant starter 自动装配
Scheduler
```

## 验收

```text
AC1 Try 成功写 TRIED
AC2 Try 失败回滚 barrier
AC3 重复 Try 幂等
AC4 Cancel 无 barrier 写 SUSPENDED
AC5 迟到 Try 被 SUSPENDED 拒绝
AC6 Confirm 幂等
AC7 Confirm 后 Cancel 拒绝
AC8 Cancel 后 Confirm 拒绝
AC9 gRPC callback 结果映射
```

## 测试

```text
TccParticipantServiceTest
TccParticipantCallbackGrpcServiceTest
TccParticipantGrpcServerTest
```

## 完成记录

```text
mvn -q test
tests=197 failures=0 errors=0 skipped=1
```
