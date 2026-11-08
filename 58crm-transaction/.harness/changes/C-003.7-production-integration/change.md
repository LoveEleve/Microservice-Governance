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

## 已完成补充

```text
Nacos gRPC 注册
真实 Nacos + MySQL + lead + quota 进程
OpenFeign Try + gRPC callback
成功/失败 E2E
```

## 不包含

```text
Sentinel
Prometheus/Grafana/告警
多 TC 实例故障注入
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
