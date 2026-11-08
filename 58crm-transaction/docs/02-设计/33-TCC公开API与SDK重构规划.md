# TCC 公开 API 与 SDK 重构规划

> 目标：把当前“能跑协议的内部工程”重构为“业务方愿意接的 TCC 框架”。
> 原则：业务方不 import proto，不手写 begin/register/report/commit，不手写 Try controller。

## 1. 当前问题

```text
TccTransactionTemplate 只有接口，没有实现
LeadAssignService 直接操作生成 proto
TccCoordinatorClient 在 e2e 模块，不在框架
QuotaController 手写 /quota/try
TCC Starter 与 Outbox/Consumer 混在一起
e2e 模块作为根模块，被误认为业务接入示例
```

## 2. 目标模块

```text
crm-tx-bom
crm-tx-tcc-api
  公共接口、注解、状态、请求/结果模型

crm-tx-tcc-protocol
  proto + 生成 stub
  只给 coordinator/client 使用

crm-tx-tcc-client
  DefaultTccTransactionTemplate
  CoordinatorGrpcClient
  Nacos/直连发现

crm-tx-tcc-participant
  TccAction 执行器
  barrier
  通用 POST /tcc/try
  Confirm/Cancel gRPC server

crm-tx-tcc-spring-boot-starter
  client + participant 自动装配
  独立于 Outbox/Consumer Starter

crm-tx-tcc-examples
  lead / quota 示例

crm-tx-tcc-coordinator
  TC server

e2e
  独立自动化测试工程，不作为业务示例
```

## 3. 公共 API

### 3.1 发起方

```java
TccResult result = tcc.execute(
        TccRequest.builder()
                .requestId("lead-assign:" + leadId + ":" + commandId)
                .bizCode("lead")
                .bizId(String.valueOf(leadId))
                .tryTimeout(Duration.ofSeconds(30))
                .branch(TccBranch.builder()
                        .resourceKey("quota:" + userId)
                        .actionName("reserveQuota")
                        .callbackEndpoint(quotaCallbackEndpoint)
                        .applicationData("{\"userId\":" + userId + "}")
                        .tryAction(ctx -> quotaClient.tryQuota(
                                new TryQuotaRequest(ctx.xid(), ctx.branchId(), userId)))
                        .build())
                .build());
```

业务方不写：

```text
begin
registerBranch
reportBranch
commit
rollback
```

### 3.2 参与方

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

业务方不写：

```text
/tcc/try controller
barrier repository
Confirm/Cancel gRPC server
action registry
```

## 4. API 模型

```text
TccRequest
  requestId
  bizCode
  bizId
  tryTimeout
  branches

TccBranch
  resourceKey
  actionName
  callbackEndpoint
  applicationData
  tryAction

TccTryContext
  xid
  branchId
  resourceKey
  actionName
  applicationData

TccResult
  xid
  status
  branches

TccBranchResult
  resourceKey
  branchId
  status
```

约束：

```text
requestId 必填，业务幂等键
branches 不能为空
applicationData <= 64KB
callbackEndpoint 必填
Try 回调必须由业务提供
```

## 5. 执行语义

DefaultTccTransactionTemplate：

```text
1. begin
2. 注册全部分支
3. 顺序执行 branch.tryAction
4. 每个 Try 后立即 report
5. 任一 Try 失败或抛异常 -> rollback
6. 全部 Try 成功 -> commit
7. commit 返回 CONFIRMING -> 原样返回
```

为什么先注册全部分支：

```text
Try 失败时 TC 能取消所有已注册分支
避免先 Try 后注册导致 Cancel 不知道分支
```

多分支第一版顺序执行；并行 Try 以后再加。

## 6. 异常语义

```text
begin/register/commit 传输失败 -> TccException
Try 返回 FAILURE 或抛业务异常 -> rollback + TccResult(CANCELLED)
Try 返回成功但 report 失败 -> rollback
commit 返回 CONFIRMING -> 返回当前状态，由业务查询
```

不把 Try 网络异常当作成功。

## 7. Participant 自动装配

```text
@TccAction 扫描
TccActionRegistry 自动注册
BarrierMapper 自动扫描
通用 POST /tcc/try
Confirm/Cancel gRPC server
```

配置：

```yaml
crm:
  tx:
    tcc:
      participant:
        enabled: true
        callback-endpoint: http://quota-service:8080
        grpc-port: 9092
```

注意：callbackEndpoint 是 TC 回调地址，不是 Nacos serviceName；TC 不发现 RM。

## 8. Client 自动装配

```yaml
crm:
  tx:
    tcc:
      client:
        enabled: true
        coordinator-service-name: crm-tx-tcc-coordinator
        deadline: 15s
```

支持：

```text
Nacos discovery
直连地址（本地测试）
连接复用
统一错误码
```

## 9. 重构切片

### C-004.1 公共模型与模板

```text
TccRequest / TccBranch / TccTryContext / TccResult
TccBranchResult
DefaultTccTransactionTemplate
Fake Coordinator 单测
```

### C-004.2 协议拆分

```text
crm-tx-tcc-protocol
proto 从 api 移出
CoordinatorGrpcClient 移入 client
业务模块不再依赖 proto
```

### C-004.3 Participant 接入

```text
@TccAction
自动注册
通用 /tcc/try
BarrierMapper 自动扫描
```

### C-004.4 Starter

```text
crm-tx-tcc-spring-boot-starter
client + participant 自动装配
属性类
关闭开关
```

### C-004.5 Examples

```text
crm-tx-tcc-examples/lead
crm-tx-tcc-examples/quota
从 e2e 迁出业务示例
e2e 只保留自动化测试
```

### C-004.6 文档

```text
README 5 分钟 quickstart
业务接入手册
Participant 接入手册
配置项表
API Javadoc
```

## 10. 验收

```text
TM 业务代码 <= 10 行
Participant 业务代码只有 TccAction
业务代码 import 0 个 proto 类
业务代码不手写 /tcc/try
业务代码不手写 begin/register/report/commit
examples 目录独立
e2e 能自动跑
README quickstart 能复制运行
```

## 11. 不做

```text
Sentinel
Prometheus/Grafana/告警
多 TC 故障注入
分片 TCC
并行 Try（先顺序）
```

## 12. 一句话

> **框架的价值不是把协议跑通，而是让业务方只写 `tryAction/confirmAction/cancelAction` 和一个 `tcc.execute(...)`。**
