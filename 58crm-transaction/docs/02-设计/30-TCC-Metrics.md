# TCC Metrics

> 读完你能做什么：知道 TCC Coordinator 暴露哪些 Micrometer 指标，以及没有 registry 时如何降级。

## 1. 原则

```text
有 MeterRegistry -> 记录
无 MeterRegistry -> no-op
指标失败不影响 TCC 状态推进
```

`TccMetrics` 不直接依赖 Spring Boot Actuator；只要 classpath 有 Micrometer registry 就能工作。

## 2. 指标

```text
crm.tx.tcc.scan{kind=due|timeout}
crm.tx.tcc.retry{phase=confirm|cancel|resume,outcome=ok|skip|error}
```

含义：

```text
scan due       一轮扫描发现的到期 branch xid 数
scan timeout   一轮扫描发现的 TRYING 超时 xid 数
retry ok       本轮推进成功
retry skip     owner 冲突/状态不允许等跳过
retry error    非预期异常
```

## 3. Scheduler 接入

```text
TccRetryScheduler
  due 数量 -> scan{due}
  timeout 数量 -> scan{timeout}
  phase2 成功 -> retry{phase,ok}
  TCC_BUSY/INVALID -> retry{phase,skip}
  RuntimeException -> retry{phase,error}
```

## 4. 测试

```text
TccMetricsTest
  scanAndRetry_countersAreRecorded
  noRegistry_isNoop
```

## 5. 当前不做

```text
Actuator endpoint 配置
Prometheus registry 依赖
Grafana dashboard
告警规则
```

这些按部署环境接入，不进入框架核心。

## 6. 一句话

> **Metrics 是旁路观测：有 registry 就计数，没有就 no-op，永远不影响 TCC 主状态机。**
