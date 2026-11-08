# C-006 多分支与 SpEL 规则 Review

> 审查对象：多分支/循环分支、重复 resourceKey、SpEL 模板与字面量 #。
> 结论：两条边界都已用单测和真实 E2E 验证。

## 1. 多分支

同一 `@TccTransactional` 方法内可以调用多个分支网关：

```java
@TccTransactional(
        requestId = "#{#command.requestId}",
        bizCode = "lead",
        bizId = "#{#command.leadId}")
public TccResult assign(AssignCommand command) {
    quotaGateway.reserve(command.userId());
    couponGateway.lock(command.couponId());
    return null;
}
```

循环分支：

```java
for (Coupon coupon : command.coupons()) {
    couponGateway.lock(coupon.id());
}
```

约束：

```text
每次分支的 resourceKey 必须唯一
重复 resourceKey -> 直接失败并 rollback
成功 Try 的分支会出现在 TccResult.branches()
```

## 2. SpEL 规则

```text
#{...} 是表达式边界，内部 #var 取参数
表达式外的 # 原样保留
"#{#userId}" -> 1
"quota:#{#userId}" -> quota:1
"order:#123" -> order:#123（字面量）
"#userId" -> 旧整段 SpEL，兼容
"'quota:' + #userId" -> 旧整段 SpEL，兼容
```

`"#123"` 这类以 # 开头的纯字面量用单引号：`"'#123'"`。

## 3. 测试

```text
TccExpressionResolverTest：7
TccAnnotationAspectTest：5
  - 单分支成功
  - 分支失败回滚
  - 多分支一起 commit
  - 循环注册 3 个唯一分支
  - 重复 resourceKey 失败回滚
mvn clean test：222 tests / 0 failures
```

## 4. 真实 E2E（两个 RM）

```text
成功：xid=1 CONFIRMED
  quota 1 assigned=1
  coupon 1 USED
  branch 1/2 CONFIRMED
  barrier 1/2 CONFIRMED
  outbox quota/coupon 各一条

失败：xid=2 CANCELLED
  quota Try 成功后被 Cancel，reserved 回到 0
  coupon 999 Try 失败，barrier SUSPENDED
  branch 1/2 CANCELLED
  HTTP 200，日志无 ERROR
```

这才是“A 成功、B 失败、TC 撤销 A”的真实链路。

## 5. 剩余边界

```text
SpEL 表达式内部暂不支持嵌套 }
失败分支的 Cancel 链路仍靠 coordinator 异步回调，未做多 TC 故障注入
```

## 6. 一句话

> **多分支不是“多写几个 @TccBranch 方法”这么简单，关键是 resourceKey 唯一；
> SpEL 也不能再靠“看见 # 就整段解析”，现在有明确的 #{...} 边界。**
