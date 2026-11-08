# C-003.7：生产化接入

## 范围

```text
Participant Starter 自动装配
后续：Nacos / Sentinel / metrics / 真实集成
```

## 已完成

```text
CrmTxTccParticipantAutoConfiguration
crm.tx.tcc.participant 开关
grpc-enabled / grpc-port
SqlSessionTemplate -> BarrierMapper
TccMetrics / Scheduler 指标
Nacos gRPC 注册器（默认关闭）
```

## 不包含

```text
Nacos 注册/配置
Sentinel
Micrometer
真实多实例压测
```

## 测试

```text
CrmTxTccParticipantAutoConfigurationTest
TccMetricsTest
```

## 完成记录

```text
mvn -q test
tests=197 failures=0 errors=0 skipped=1
```
