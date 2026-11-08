# C-004：TCC 公开 SDK 重构

## 用户故事

作为业务方，我希望只定义 TccAction 和一次 tcc.execute，而不是理解 Coordinator 协议。

## 范围

```text
TccRequest/TccBranch/TccResult
DefaultTccTransactionTemplate
proto 移出 api
CoordinatorGrpcClient 移入 client
@TccAction 自动注册
通用 /tcc/try
TCC Starter
examples 分离
```

## 不做

```text
Sentinel
Prometheus/Grafana/告警
多 TC 故障注入
并行 Try
```

## 验收

```text
业务方不 import proto
业务方不手写 begin/register/report/commit
业务方不手写 Try controller
TM 业务代码 <= 10 行
Participant 只实现 TccAction
README quickstart 可复制运行
```

## 当前状态

```text
C-004.1 已完成：公共模型 + DefaultTccTransactionTemplate + 重复 branch 校验
C-004.2 已完成：proto 拆分 + CoordinatorGrpcClient + e2e lead 使用公共模板
C-004.3 已完成：@TccActionHandler + 自动注册 + 通用 /tcc/try + E2E 复跑
C-004.4 已完成：TCC 独立 Starter（client/participant 默认关闭，显式开启）（client/participant 默认关闭）
C-004.5 已完成：examples 迁移
C-004.6 已完成：quickstart + 迁移后 E2E 复跑
```
