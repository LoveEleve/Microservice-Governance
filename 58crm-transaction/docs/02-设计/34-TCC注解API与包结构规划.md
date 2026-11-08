# TCC 注解 API 与包结构规划

> 目标：业务方默认用注解接入；builder 只作为高级/动态分支 API。
> 同时把公共 API、内部协议、服务端、示例的包边界定死。

## 1. 最终业务用法

### 1.1 TM

```java
@TccTransactional(
        requestId = "#command.requestId",
        bizCode = "lead",
        bizId = "#command.leadId",
        tryTimeoutSeconds = 30
)
public void assign(AssignLeadCommand command) {
    quotaClient.tryQuota(command.userId());
}
```

不再出现：

```text
TccRequest.builder()
TccBranch.builder()
begin/register/report/commit
```

### 1.2 RM

```java
@Component
@TccActionHandler("reserveQuota")
public class QuotaTccAction implements TccAction {
    // try / confirm / cancel
}
```

RM 仍只实现 TccAction。

### 1.3 分支声明

分支标在 Feign 方法上：

```java
@TccBranch(
        actionName = "reserveQuota",
        resourceKey = "#userId",
        callbackEndpoint = "${quota.callback-endpoint}",
        applicationData = "#userId"
)
@PostMapping("/tcc/try")
TccTryResponse tryQuota(@RequestParam long userId);
```

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
AOP around Feign 方法
  -> 从 ThreadLocal 取当前 TCC 事务
  -> registerBranch
  -> 设置 TccBranchContext(xid, branchId)
  -> Feign RequestInterceptor 注入 X-Tcc-Xid / X-Tcc-Branch-Id
  -> 调用 RM 通用 /tcc/try
  -> 成功 report TRY_SUCCESS
  -> 失败 report TRY_FAIL + 抛 TccTryFailedException
```

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

## 3. 为什么需要 AOP + Feign 拦截器

```text
分支不是方法参数里的普通对象
xid/branchId 必须由框架注入请求
callbackEndpoint 属于参与者配置，不属于业务 Try 参数
```

当前 builder 把这些问题显式丢给业务；注解模式必须由框架处理。

## 4. 两条 API 线

```text
默认：
  @TccTransactional + @TccBranch

高级：
  TccRequest / TccBranch / TccTransactionTemplate
  用于动态分支、运行时生成分支、测试
```

builder 不再出现在 quickstart。

## 5. 目标模块与包

```text
crm-tx-tcc-api
  com.wuba.crm.tx.tcc.api
    TccTransactionTemplate
    TccRequest / TccBranch / TccResult
    TccAction / TccActionHandler
    TccTransactional / TccBranch

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
    @TccTransactional AOP
    Feign interceptor

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
TccRequest / TccBranch / TccResult / TccBranchResult
TccAction / TccActionHandler
TccTransactional / TccBranch
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

### C-005.3 AOP + Feign

```text
@TccTransactional aspect
@TccBranch aspect
Feign RequestInterceptor
参与方通用 /tcc/try header
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
Feign 方法只出现 @TccBranch
业务不 import proto
业务不手写 begin/register/report/commit
业务不手写 /tcc/try
包名 api/protocol/internal 边界清晰
```

## 10. 风险

```text
Spring AOP 对 Feign 代理的拦截需要 PoC
SpEL 取 requestId/resourceKey 需要限制表达式范围
xid/branchId 用 header 注入，需要参与方兼容
注解失败时必须触发外层 rollback
```

## 11. 一句话

> **注解是业务默认入口，builder 是高级内核；两者共用同一套 TCC 语义，包结构按 public/protocol/internal 三层切干净。**
