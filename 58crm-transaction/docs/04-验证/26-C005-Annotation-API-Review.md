# C-005 注解 API 与包边界 Review

> 审查对象：@TccTransactional / @TccBranch、TccContext、starter 条件装配、
> lead 注解迁移、TM-only 依赖边界、真实 E2E。
> 结论：注解链路已打通；builder 降为高级 API；包边界完成第一轮收敛。

## 1. 注解写法

TM：

```java
@TccTransactional(
        requestId = "#command.requestId",
        bizCode = "lead",
        bizId = "#command.leadId",
        tryTimeoutSeconds = 30)
public TccResult assign(AssignLeadCommand command) {
    quotaGateway.reserve(command.userId());
    return null;
}
```

分支：

```java
@TccBranch(
        actionName = "reserveQuota",
        resourceKey = "'quota:' + #userId",
        callbackEndpoint = "${quota.callback-endpoint}",
        applicationData = "#userId")
public TccTryResult reserve(long userId) {
    TccContext context = TccContext.current();
    // 用 context.xid()/branchId() 调下游 Try
}
```

## 2. 关键设计

```text
@TccTransactional：begin -> 执行业务 -> commit；异常 rollback
@TccBranch：register -> 绑定 TccContext -> 执行业务 -> report
分支失败：外层 rollback；返回 TccResult 时返回 CANCELLED，HTTP 200
分支必须走独立 Bean 代理，避免自调用绕过 AOP
builder（TccRequest/TccBranchSpec）保留为动态分支/高级 API
```

## 3. 装配边界

```text
TccAnnotationAutoConfiguration：
  @AutoConfigureAfter(CrmTxTccClientAutoConfiguration)
  @ConditionalOnBean(TccTransactionManager)
  crm.tx.tcc.annotation.enabled 默认 true
```

RM-only 应用没有 TccTransactionManager，不会创建注解 aspect。

## 4. TM-only 依赖边界

```text
crm-tx-tcc-spring-boot-starter
  crm-tx-tcc-participant 标记 optional
```

原因：participant 依赖 MyBatis-Plus；TM-only 应用不应被拖入 DataSource
自动配置。RM 显式增加 `crm-tx-tcc-participant` 依赖。

## 5. 真实 E2E

环境：

```text
Nacos + MySQL + coordinator + lead（注解）+ quota（@TccActionHandler）
```

成功：

```text
HTTP 200 {"xid":1,"status":"CONFIRMED"}
quota assigned=1
global/branch/barrier CONFIRMED
outbox quota-confirmed:1:1 INIT
```

失败：

```text
HTTP 200 {"xid":2,"status":"CANCELLED"}
quota 未扣减
branch CANCELLED
barrier SUSPENDED
```

日志无 ERROR/Exception。

## 6. 验证

```text
tests=215 failures=0 errors=0 skipped=1
```

## 7. 仍未完成

```text
低层 DTO（TccBeginRequest 等）还在公共 api
crm-tx-bom 未建
@TccBranch 暂不支持多分支/循环分支
SpEL 字面量含 # 的转义规则未文档化
多 TC / Confirm/Cancel 故障注入未做
```

## 8. 一句话

> **注解入口已经不是 demo：业务只写 @TccTransactional + @TccBranch，
> 包边界和 TM/RM 依赖隔离也已经被真实 E2E 验证。**
