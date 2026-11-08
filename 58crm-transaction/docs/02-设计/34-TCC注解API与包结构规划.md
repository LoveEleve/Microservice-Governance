# TCC 注解 API 与包结构规划

> 目标：业务方默认用注解接入；builder 只作为高级/动态分支 API。
> 同时把公共 API、内部协议、服务端、示例的包边界定死。

## 1. 最终业务用法

### 1.1 TM

```java
@TccTransactional(
        requestId = "#{#command.requestId}",
        bizCode = "lead",
        bizId = "#{#command.leadId}",
        tryTimeoutSeconds = 30
)
public TccResult assign(AssignLeadCommand command) {
    quotaGateway.reserve(command.userId());   // RM1
    couponGateway.lock(command.couponId());   // RM2
    return null;
}
```

不再出现：

```text
TccRequest.builder()
TccBranchSpec.builder()
begin/register/report/commit
```

### 1.2 RM

```java
@Component
@TccActionHandler("reserveQuota")
public class QuotaTccAction implements TccAction {
    // try / confirm / cancel
}

@Component
@TccActionHandler("lockCoupon")
public class CouponTccAction implements TccAction {
    // try / confirm / cancel
}
```

RM 仍只实现 TccAction；Lead 只做 TM。

### 1.3 分支声明

分支标在独立 Spring Bean 的本地方法上，方法体负责通过 Feign 调下游 Try：

```java
@Service
public class QuotaBranchGateway {

    @TccBranch(
            actionName = "reserveQuota",
            resourceKey = "quota:#{#userId}",
            callbackEndpoint = "${quota.callback-endpoint}",
            applicationData = "#{#userId}")
    public TccTryResult reserve(long userId) {
        TccContext context = TccContext.current();
        TryQuotaResponse response = quotaClient.tryQuota(new TryQuotaRequest(
                context.xid(), context.branchId(), "reserveQuota", payload(userId)));
        return response.success() ? TccTryResult.SUCCESS : TccTryResult.FAILURE;
    }
}
```

coupon 分支同理：

```java
@TccBranch(
        actionName = "lockCoupon",
        resourceKey = "coupon:#{#couponId}",
        callbackEndpoint = "${coupon.callback-endpoint}",
        applicationData = "#{#couponId}")
public TccTryResult lock(long couponId) {
    TccContext context = TccContext.current();
    // Feign 调 coupon /tcc/try
}
```

不直接标 Feign 方法：Feign 是下游契约，不应该反向依赖 TCC 注解；
本地网关方法还能显式控制 Try 返回值、applicationData 和异常。

### 1.4 表达式规则

```text
#{...}        表达式边界，内部 #var 取参数；表达式外 # 原样保留
"#{#userId}"  取 userId
"quota:#{#userId}"  拼出 quota:1
"order:#123"  没有 #{}，按字面量处理
"#userId"     旧写法，整段 SpEL 兼容
"'quota:' + #userId"  旧写法，整段 SpEL 兼容
```

循环分支时 resourceKey 必须每次唯一；同一事务内重复 key 直接失败。

## 2. 注解如何工作

### 2.1 @TccTransactional

```text
AOP around 业务方法
  -> 创建 TccTransactionContext
  -> begin（Coordinator）
  -> 执行业务方法
  -> 正常返回 commit
  -> 异常 rollback
```

### 2.2 @TccBranch

```text
AOP around 本地网关方法
  -> 从 ThreadLocal 取当前 TCC 事务
  -> registerBranch
  -> 设置 TccContext(xid, branchId)
  -> 方法体用 context 调 Feign /tcc/try
  -> 成功 report TRY_SUCCESS
  -> 失败 report TRY_FAIL + 抛 TccBranchFailedException
```

分支失败由外层 @TccTransactional 转成 rollback；如果业务方法返回
TccResult，则返回 CANCELLED 而不是 HTTP 500。

### 2.3 Participant 通用入口

```text
POST /tcc/try
Header:
  X-Tcc-Xid
  X-Tcc-Branch-Id
Body:
  actionName
  applicationData
```

兼容旧 body xid/branchId。

## 3. 为什么需要 AOP

```text
分支不是方法参数里的普通对象
xid/branchId 必须由框架创建并暴露给业务
callbackEndpoint 属于参与者配置，不属于业务 Try 参数
```

当前 builder 把这些问题显式丢给业务；注解模式由 AOP 完成
register/report，业务只在方法体里传 xid/branchId 调下游。

## 4. 两条 API 线

```text
默认：
  @TccTransactional + @TccBranch

高级：
  TccRequest / TccBranchSpec / TccTransactionTemplate
  用于动态分支、运行时生成分支、测试
```

builder 不再出现在 quickstart。

## 5. 目标模块与包

```text
crm-tx-bom
  统一依赖版本

crm-tx-tcc-api
  com.wuba.crm.tx.tcc.api
    TccTransactionTemplate
    TccRequest / TccBranchSpec / TccResult
    TccAction / TccActionHandler
    TccTransactional / TccBranch / TccContext

crm-tx-tcc-protocol
  com.wuba.crm.tx.tcc.protocol.grpc
    generated stubs

crm-tx-tcc-client
  com.wuba.crm.tx.tcc.client
    DefaultTccTransactionTemplate
    TccCoordinatorEndpointProvider
  com.wuba.crm.tx.tcc.client.internal
    CoordinatorGrpcClient
    TccCoordinatorOperations

crm-tx-tcc-participant
  com.wuba.crm.tx.tcc.participant
    TccParticipantService
    TccActionRegistry / TccActionRegistrar
  com.wuba.crm.tx.tcc.participant.barrier
  com.wuba.crm.tx.tcc.participant.web
  com.wuba.crm.tx.tcc.participant.grpc

crm-tx-tcc-coordinator
  com.wuba.crm.tx.tcc.coordinator
    service / persistence / grpc / nacos

crm-tx-tcc-spring-boot-starter
  com.wuba.crm.tx.tcc.starter
    client/participant auto config
    @TccTransactional / @TccBranch AOP
    SpEL resolver

crm-tx-tcc-examples
  lead / quota

e2e
  SQL + run scripts + automated test
```

## 6. 低层 DTO 迁移

从公共 `api` 移出：

```text
TccBeginRequest
TccBranchRegisterRequest
TccBranchReportRequest
TccCommitResult
TccRollbackResult
TccQueryResult
TccBranchInfo
TccConfirmRequest
TccCancelRequest
TccCallbackResult
```

去向：

```text
client.internal 或 protocol.model
```

公共保留：

```text
TccRequest / TccBranchSpec / TccResult / TccBranchResult
TccAction / TccActionHandler
TccTransactional / TccBranch / TccContext
TccTransactionTemplate
状态枚举 / TccException
```

## 7. 包管理修正

```text
1. proto java_package 改为 com.wuba.crm.tx.tcc.protocol.grpc
2. 低层 DTO 移出 api
3. CoordinatorGrpcClient / TccCoordinatorOperations 移入 client.internal
4. 增加 crm-tx-bom 统一版本
5. 通用 starter 不再包含 TCC
6. examples 与 e2e 分离
```

## 8. 实施切片

### C-005.1 包与协议

```text
proto package 改名
低层 DTO 迁移
client internal 分包
crm-tx-bom
```

### C-005.2 注解内核

```text
TccTransactional / TccBranch
TccTransactionContext / TccBranchContext
TccTransactionManager
```

### C-005.3 AOP

```text
@TccTransactional aspect
@TccBranch aspect
SpEL resolver
分支失败 -> rollback/CANCELLED
```

### C-005.4 示例与文档

```text
lead 改注解写法
quota 保持 TccAction
quickstart 重写
真实 E2E 复跑
```

## 9. 验收

```text
lead 业务方法只出现 @TccTransactional
本地分支网关方法只出现 @TccBranch
业务不 import proto
业务不手写 begin/register/report/commit
业务不手写 /tcc/try
包名 api/protocol/internal 边界清晰
```

## 10. 风险

```text
@TccBranch 必须走 Spring 代理，同类内部调用会绕过 AOP
SpEL 表达式内部暂不支持嵌套 }
旧写法整段 SpEL 仍保留，新代码优先 #{...}
注解失败时必须触发外层 rollback
```

## 11. 一句话

> **注解是业务默认入口，builder 是高级内核；两者共用同一套 TCC 语义，包结构按 public/protocol/internal 三层切干净。**

## 12. 实施状态

```text
C-005.1 proto 包名 / client internal：已完成
C-005.2 注解内核：已完成
C-005.3 AOP：已完成
C-005.4 lead/quickstart/E2E：已完成
C-005.5 低层 DTO / BOM：已完成
C-006 多分支/SpEL 规则：已完成
```
