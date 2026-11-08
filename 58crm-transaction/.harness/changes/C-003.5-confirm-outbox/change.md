# C-003.5：Confirm 事务契约与 Outbox 示例

## 用户故事

作为业务方，我希望在 Confirm 的同一个本地事务里写业务终态和 Outbox，保证不产生假成功事件。

## 范围

```text
业务 TccAction 示例
Confirm + Outbox 同事务测试
框架边界文档
```

## 明确不做

```text
框架自动生成 eventKey/eventType/payload
框架自动写业务事件
```

## 验收

```text
AC1 Confirm 动作写业务终态
AC2 Confirm 动作调用 OutboxPublisher.publish
AC3 barrier、业务、Outbox 同事务提交
AC4 Confirm 返回 RETRYABLE 时三者一起回滚
AC5 框架代码不生成业务事件
```

## 测试

```text
TccOutboxTransactionTest
```


## 完成记录

```text
mvn -q test
tests=197 failures=0 errors=0 skipped=1
```
