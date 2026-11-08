# C-003.3：Commit / Rollback / Callback

## 用户故事

作为 TM，我可以提交或回滚全局事务；Coordinator 在 owner lease 保护下推进分支，并回调 RM。

## 范围

```text
Commit
Rollback
Query
owner lease
branch 预推进
TccParticipantClient
gRPC participant callback
gRPC coordinator Commit/Rollback/Query
```

## 不包含

```text
Participant barrier 实现
Scheduler 自动重试
Outbox 同事务
TLS / 鉴权
```

## 验收

```text
AC1 Commit 全部 Confirm 成功 -> global CONFIRMED
AC2 Confirm 可重试 -> 保持 CONFIRMING
AC3 Confirm 永久失败 -> branch DEAD/global FAILED
AC4 Rollback 全部 Cancel 成功 -> global CANCELLED
AC5 owner lease 有效 -> TCC_BUSY
AC6 旧 owner 不能覆盖新 owner
AC7 RM callback 在 DB 事务外
AC8 Query 返回 global + branches
```

## 测试

```text
Commit 成功/可重试/永久失败
Rollback 成功
Query
owner/branch 状态条件
gRPC adapter 映射
```

## 文档

```text
docs/02-设计/25-TCC二阶段与Callback.md
```


## 完成记录

```text
TccPhase2Coordinator
TccParticipantClient / GrpcTccParticipantClient
Commit / Rollback / Query gRPC adapter
owner lease
branch 预推进
```

验证：

```text
mvn -q test
tests=190 failures=0 errors=0 skipped=1
```
