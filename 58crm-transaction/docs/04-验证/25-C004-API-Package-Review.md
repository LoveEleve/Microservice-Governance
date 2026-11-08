# C-004 API 与包结构 Review

> 结论：当前是“半注解、半 builder”的过渡态，包结构也没有按公共/内部边界收干净。
> 在继续写功能前，必须先统一接入 API 和包结构。

## 1. P0：TM 不是注解使用方式

当前：

```text
RM：@TccActionHandler + TccAction
TM：TccRequest.builder() + tcc.execute(...)
```

问题：

```text
同一个框架，参与方是注解，发起方是 builder
业务方仍然要理解 TccRequest/TccBranch
没有 @TccTransactional
```

目标注解：

```java
@TccTransactional
public void assign(long leadId, long userId) {
    quotaClient.tryQuota(...);
}
```

但注解要解决分支发现：

```text
分支在哪里声明？
resourceKey/applicationData 怎么动态取？
callbackEndpoint 怎么配置？
Feign 调用参数怎么注入 xid/branchId？
```

可选方案：

```text
A. @TccTransactional + @TccBranch 标在 Feign 方法上
B. @TccTransactional + 本地 @TccBranch 方法
C. 保留 builder 作为高级 API，再提供注解门面
```

倾向：

```text
C：builder 是内核，@TccTransactional 是常用门面
```

不能只加一个空注解，必须把分支发现和上下文注入一起做。

## 2. P0：generated proto 包名仍属于 api

当前：

```text
crm-tx-tcc-protocol
  src/main/proto/.../tcc.proto
  generated package: com.wuba.crm.tx.tcc.api.grpc
```

问题：

```text
模块叫 protocol，包却叫 api
业务方看到 api.grpc 会误以为它是公共 API
```

应改为：

```text
com.wuba.crm.tx.tcc.protocol.grpc
```

## 3. P0：低层 DTO 暴露在公共 API

当前仍在 `crm-tx-tcc-api`：

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

问题：

```text
业务方会以为这些是公共契约
实际它们是 client/coordinator/participant 内部协议
```

目标：

```text
公共：TccRequest/TccBranch/TccResult/TccAction/TccTransactionTemplate
内部：client.internal / protocol.model
```

## 4. P1：client 内部实现是 public

```text
TccCoordinatorOperations
CoordinatorGrpcClient
```

应放在：

```text
com.wuba.crm.tx.tcc.client.internal
```

业务只依赖：

```text
TccTransactionTemplate
```

## 5. P1：Starter 命名曾经混乱

```text
crm-tx-spring-boot-starter
  曾经包含 TCC participant
```

现在已拆：

```text
crm-tx-spring-boot-starter       Outbox/Consumer
crm-tx-tcc-spring-boot-starter   TCC client/participant
```

需要保证不再回退。

## 6. P1：Maven 版本管理

缺：

```text
crm-tx-bom
```

当前根 pom 直接 import Boot/Spring Cloud/Alibaba BOM；业务方依赖时只能复制版本号。

## 7. 目标包结构

```text
crm-tx-tcc-api
  com.wuba.crm.tx.tcc.api
    TccRequest
    TccBranch
    TccResult
    TccAction
    TccActionHandler
    TccTransactional（待实现）
    TccTransactionTemplate

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
    TccActionRegistry
    TccActionRegistrar
    barrier/
    web/
    grpc/

crm-tx-tcc-coordinator
  com.wuba.crm.tx.tcc.coordinator
    service/
    persistence/
    grpc/
    nacos/

crm-tx-tcc-spring-boot-starter
  com.wuba.crm.tx.tcc.starter
```

## 8. 重构顺序

```text
1. 先改 proto java_package -> protocol.grpc
2. 低层 DTO 移出公共 api
3. client internal 分包
4. 设计 @TccTransactional + 分支发现
5. 加 crm-tx-bom
6. 重写 quickstart
```

## 9. 一句话

> **当前 builder 只是内核 API，注解门面和包边界还没定稿；不把这层定死，继续加功能只会更乱。**
