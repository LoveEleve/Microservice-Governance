# 框架接入层深度 Review

> 结论先说：当前代码是“内部协议验证 + E2E 工程”，不是“开源框架的接入层”。
> 业务方不应该看到 begin/register/report/commit，也不应该依赖生成 proto。

## 1. 我犯的方向性错误

### P0：没有真正的 TM 公共 API

现状：

```text
TccTransactionTemplate 只有接口
没有实现
```

业务方实际写的是：

```java
coordinator.begin(...)
coordinator.registerBranch(...)
quotaClient.tryQuota(...)
coordinator.report(...)
coordinator.commit(...)
```

这是协议编排，不是业务接入。

### P0：业务代码依赖生成 proto

`LeadAssignService` 和 `TccCoordinatorClient` 直接 import：

```text
com.wuba.crm.tx.tcc.api.grpc.BeginRequest
RegisterBranchRequest
TccCoordinatorGrpc
```

后果：

```text
proto 一改，业务方跟着改
业务方必须理解 gRPC 状态码和 proto 枚举
框架无法替换传输实现
```

### P0：Participant Try 入口是业务方手写的

现在 quota 需要自己写：

```java
@PostMapping("/quota/try")
public TryQuotaResponse tryQuota(...) {
    participantService.tryAction(...);
}
```

这就是样板代码。框架应该提供通用 Try endpoint。

### P1：Starter 不是一个清晰的 TCC Starter

现状：

```text
crm-tx-spring-boot-starter 同时装 Outbox/Consumer/TCC
CrmTxProperties 没有 TCC client 配置
@EnableCrmTx 只 import 了 CrmTxAutoConfiguration
TCC participant 需要自己找 Mapper
```

开放框架应该拆成可组合模块，而不是一个大 Starter。

### P1：示例和框架混在一起

`crm-tx-tcc-e2e-lead`、`crm-tx-tcc-e2e-quota` 作为根模块存在，
业务方看到会以为这就是接入方式。

正确做法：

```text
框架 modules
examples modules
e2e test harness
```

分开。

### P1：客户端能力不足

`TccCoordinatorClient` 在 e2e 模块里，不在框架里；且：

```text
每次调用新建 channel
没有连接复用
没有统一错误模型
没有 retry/deadline 配置
没有客户端拦截器
```

### P2：没有开源项目的基本门面

缺少：

```text
TCC 快速开始
API 文档
配置项说明
版本/兼容性策略
示例模块
架构图
```

## 2. 正确的开源结构

```text
crm-tx-bom
crm-tx-tcc-api
  TccAction
  TccTransactionTemplate（公共接口）
  TccRequest / TccBranch
  TccResult / TccError

crm-tx-tcc-client
  DefaultTccTransactionTemplate
  CoordinatorGrpcClient
  Nacos/直连地址解析

crm-tx-tcc-participant
  TccAction 执行器
  barrier
  通用 Try endpoint
  Confirm/Cancel gRPC server

crm-tx-tcc-spring-boot-starter
  client + participant 自动装配

crm-tx-tcc-examples
  lead/quota 示例

e2e
  真实进程测试，不作为业务接入示例
```

## 3. 业务方应该看到的 API

```java
TccResult result = tcc.execute(
        TccRequest.builder()
                .requestId("lead-assign:" + leadId + ":" + commandId)
                .bizCode("lead")
                .bizId(String.valueOf(leadId))
                .tryTimeout(Duration.ofSeconds(30))
                .branch(TccBranchSpec.builder()
                        .resourceKey("quota:" + userId)
                        .actionName("reserveQuota")
                        .callbackEndpoint(quotaCallbackEndpoint)
                        .applicationData("{\"userId\":" + userId + "}")
                        .tryAction(() -> quotaClient.tryQuota(...))
                        .build())
                .build());
```

业务方不再手写：

```text
begin
registerBranch
report
commit
rollback
```

异常、超时、回滚由模板统一处理。

## 4. Participant 应该看到的 API

```java
@Component
@TccAction("reserveQuota")
public class QuotaTccAction implements TccAction {

    @Override
    public TccTryResult tryAction(BusinessActionContext context) {
        // 预占
    }

    @Override
    public TccPhaseResult confirmAction(BusinessActionContext context) {
        // 终态 + Outbox
    }

    @Override
    public TccPhaseResult cancelAction(BusinessActionContext context) {
        // 释放
    }
}
```

Starter 负责：

```text
注册 action
提供 POST /tcc/try
提供 Confirm/Cancel gRPC server
barrier repository
```

## 5. 重构顺序

```text
1. crm-tx-tcc-client：DefaultTccTransactionTemplate
2. TccRequest / TccBranch / TccResult 公共模型
3. CoordinatorGrpcClient 从 e2e 移到框架
4. @TccAction 注册 + 通用 Try endpoint
5. 拆分 TCC Starter
6. 把 lead/quota 移到 examples
7. 重写快速开始
8. E2E 改为 examples 的自动化测试
```

## 6. 验收标准

```text
业务方代码 <= 10 行完成一次 TCC
业务方不 import 任何 proto 类
业务方不手写 begin/register/report/commit
业务方不手写 /tcc/try controller
示例模块和框架模块目录分离
README 有 5 分钟 quickstart
```

## 7. 一句话

> **当前 E2E 只能证明协议能跑，不能证明框架可用；先重构接入层，再谈继续加功能。**
