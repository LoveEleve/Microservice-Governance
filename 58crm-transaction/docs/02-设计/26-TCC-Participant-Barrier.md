# TCC Participant Barrier

> 读完你能做什么：知道 RM 侧 Try/Confirm/Cancel 如何和 barrier、业务动作在同一个本地事务里执行。

## 1. 模块

```text
crm-tx-tcc-participant
├── barrier
│   ├── BarrierEntity
│   ├── BarrierMapper
│   ├── BarrierRepository
│   ├── MybatisBarrierRepository
│   └── TccParticipantService
├── action
│   └── TccActionRegistry
├── config
│   └── TccParticipantConfiguration
└── grpc
    ├── TccParticipantCallbackGrpcService
    └── TccParticipantGrpcServer
```

## 2. barrier 表

```sql
CREATE TABLE t_branch_barrier (
  xid          BIGINT       NOT NULL,
  branch_id    BIGINT       NOT NULL,
  action_name  VARCHAR(128) NOT NULL,
  status       VARCHAR(16)  NOT NULL,
  created_at   DATETIME(3)  NOT NULL,
  updated_at   DATETIME(3)  NOT NULL,
  PRIMARY KEY (xid, branch_id)
);
```

状态：

```text
TRIED
CONFIRMED
CANCELLED
SUSPENDED
```

## 3. Try

```text
BEGIN
  lock barrier
  已 TRIED/CONFIRMED -> 幂等成功
  已 CANCELLED/SUSPENDED -> 失败
  无记录 -> INSERT TRIED
          -> action.tryAction
COMMIT
```

失败：

```text
action 返回 FAILURE
  -> rollback-only
  -> barrier 和业务预占一起回滚
```

## 4. Confirm

```text
BEGIN
  lock barrier
  不存在 -> INVALID_STATE
  CONFIRMED -> 幂等成功
  CANCELLED/SUSPENDED -> INVALID_STATE
  TRIED -> action.confirmAction
        -> barrier CONFIRMED
COMMIT
```

action 返回 `RETRYABLE` / `PERMANENT_FAILURE`：

```text
rollback-only
barrier 保持 TRIED
由 TC 重试或标记 DEAD
```

## 5. Cancel

```text
BEGIN
  lock barrier
  不存在 -> INSERT SUSPENDED -> 成功
  SUSPENDED/CANCELLED -> 幂等成功
  CONFIRMED -> INVALID_STATE
  TRIED -> action.cancelAction
        -> barrier CANCELLED
COMMIT
```

空回滚：

```text
Cancel 先到，没有 Try
  -> SUSPENDED

迟到 Try:
  -> 看到 SUSPENDED -> 拒绝
```

## 6. action 注册

```text
TccActionRegistry
  register(actionName, action)
  require(actionName)
```

不允许重复 actionName。

## 7. gRPC callback

```text
TccParticipantCallbackGrpcService
  Confirm -> TccParticipantService.confirm
  Cancel  -> TccParticipantService.cancel
```

错误映射：

```text
TCC_BUSY / INTERNAL -> RETRYABLE
TCC_NOT_FOUND / INVALID_STATE -> PERMANENT_FAILURE
```

## 8. 配置

```text
TccParticipantConfiguration
  @MapperScan(barrier, annotationClass=Mapper.class)
  @EnableTransactionManagement(proxyTargetClass=true)

端口：
  crm.tx.tcc.participant.grpc-port: 9091
```

## 9. 测试

```text
Try 成功/重复/失败回滚
Confirm 成功/重复/无 barrier
Cancel 空回滚/成功/重复
Cancel 后迟到 Try
Confirm 后再 Cancel
gRPC 结果映射
server 启动/关闭
```

## 10. 未做

```text
Confirm 同事务写 Outbox
participant starter 自动装配
真实 gRPC integration test
```

## 11. 一句话

> **barrier 是 RM 本地事务里的状态闸门：Try 先插 TRIED，Confirm/Cancel 锁行改终态，空回滚写 SUSPENDED，迟到 Try 看到 SUSPENDED 必须拒绝。**
