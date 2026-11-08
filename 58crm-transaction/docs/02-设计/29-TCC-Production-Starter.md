# TCC Participant Starter

> 读完你能做什么：知道业务应用引入 Starter 后，Participant barrier 和 callback server 如何自动装配。

## 1. 自动装配

```text
CrmTxTccParticipantAutoConfiguration
  TccActionRegistry
  BarrierRepository
  TccParticipantService
  TccParticipantCallbackGrpcService
  TccParticipantGrpcServer
```

条件：

```text
crm.tx.tcc.participant.enabled=true
存在 BarrierMapper，或存在 SqlSessionTemplate
```

如果存在 `SqlSessionTemplate`，Starter 自动创建 `BarrierMapper`。

## 2. 业务方最少需要

```text
自己的 TccAction 实现注册
数据库 t_branch_barrier
SqlSessionTemplate（通常由 MyBatis Starter 提供）
```

如果业务没有 `SqlSessionTemplate`，则需要显式提供 `BarrierMapper`。

注册：

```java
@Bean
TccActionRegistration leadAssignRegistration(
        TccActionRegistry registry, LeadAssignAction action) {
    return () -> registry.register("assignLead", action);
}
```

或启动时直接调用。

## 3. 配置

```yaml
crm:
  tx:
    enabled: true
    tcc:
      participant:
        enabled: true
        grpc-enabled: true
        grpc-port: 9091
```

## 4. 为什么不是直接扫描 Mapper 包

Starter 不替业务扫描 Mapper 包，避免和业务自己的
`@MapperScan`/多数据源配置冲突。

优先从 `SqlSessionTemplate` 创建 `BarrierMapper`；没有时再要求业务显式提供。

## 5. 当前不做

```text
Nacos 注册/配置
Sentinel 限流
Micrometer metrics
TLS/鉴权
真实多实例压测
```

## 6. 一句话

> **Starter 自动装配 barrier、执行器和 callback server，但 Mapper 扫描和 TccAction 注册仍由业务方显式提供。**
