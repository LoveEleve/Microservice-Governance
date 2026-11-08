# TCC Confirm 与 Outbox 边界

> 读完你能做什么：明确 Confirm 的 Outbox 由谁写、在哪个事务里写，以及框架只负责什么。

## 1. 结论

```text
框架不生成业务事件
业务 TccAction.confirmAction 写业务终态和 Outbox
框架保证 barrier、业务动作、Outbox 在同一个本地事务
```

## 2. 为什么不是框架写

框架不知道：

```text
eventKey
eventType
payload
业务主键语义
```

如果框架自动生成事件，只能靠猜，或者要求业务再传一份重复配置，最终会变成：

```text
barrier 由框架写
业务状态由业务写
消息由框架按未知语义写
```

三者无法保证业务正确性。

## 3. 正确写法

业务动作示例：

```java
public final class LeadAssignAction implements TccAction {

    private final JdbcTemplate jdbc;
    private final OutboxPublisher outbox;

    @Override
    public TccPhaseResult confirmAction(BusinessActionContext context) {
        jdbc.update("UPDATE t_lead SET status = 'ASSIGNED' WHERE id = 1");
        outbox.publish(new BusinessEvent(
                "lead-assigned:" + context.xid() + ":" + context.branchId(),
                "lead",
                "L-1",
                "LEAD_ASSIGNED",
                "{}"));
        return TccPhaseResult.SUCCESS;
    }

    // tryAction/cancelAction 略
}
```

调用链：

```text
TccParticipantService.confirm
  BEGIN
    lock barrier TRIED
    action.confirmAction
      business update
      OutboxPublisher.publish  // MANDATORY，复用当前事务
    barrier -> CONFIRMED
  COMMIT
```

`MybatisOutboxPublisher.publish` 标注：

```java
@Transactional(propagation = Propagation.MANDATORY)
```

所以它不能自己开事务；必须在 Confirm 事务内。

## 4. 失败语义

```text
Confirm action 返回 RETRYABLE/PERMANENT_FAILURE
  -> setRollbackOnly
  -> 业务终态、Outbox、barrier 更新一起回滚
```

```text
Confirm action SUCCESS
  -> barrier CONFIRMED
  -> 业务终态和 Outbox 一起提交
```

## 5. 验证

测试：

```text
TccOutboxTransactionTest
  confirm_writesBusinessAndOutboxInSameTransaction
  confirmRetryable_rollsBackBusinessAndOutbox
```

断言：

```text
业务状态 ASSIGNED
barrier CONFIRMED
t_local_message 1 条 INIT

失败时：
业务状态仍 ASSIGNING
barrier 仍 TRIED
t_local_message 0 条
```

## 6. 一句话

> **Confirm 的 Outbox 是业务动作的一部分；框架只提供事务壳和 barrier，绝不替业务生成业务事件。**
