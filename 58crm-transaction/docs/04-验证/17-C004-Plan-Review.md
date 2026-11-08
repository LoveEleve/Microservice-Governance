# C-004 规划 Review

> 审查对象：TCC 公开 API/SDK 重构规划。
> 结论：方向正确，但几处细节必须先定，否则重构会再次做成内部协议包装。

## 1. P0：callbackEndpoint 语义错误

原规划：

```yaml
callback-endpoint: http://quota-service:8080
```

问题：

```text
TC -> RM 是 gRPC callback
不是 HTTP /quota/try
```

修正：

```yaml
callback-endpoint: quota-service:9092
```

并明确：

```text
TC 不发现 RM
callbackEndpoint 必须是稳定 gRPC host:port
不能只写 Nacos serviceName
```

## 2. P0：公开/内部接口没切干净

公开：

```text
TccTransactionTemplate.execute
TccRequest / TccBranch / TccResult
TccAction
```

内部：

```text
TccBeginRequest / TccBranchRegisterRequest
TccCoordinatorGrpc
CoordinatorGrpcClient
```

否则业务方仍会看到 begin/register/report。

## 3. P0：examples 和 framework 没分离

```text
crm-tx-tcc-e2e-lead/quota 不能作为框架模块发布
必须移到 crm-tx-tcc-examples
e2e 只保留自动化测试
```

否则开源用户会照抄 E2E 的裸协议代码。

## 4. P1：@TccAction 扫描方式

必须：

```text
按 Bean 类型扫描
不按代理类注解扫描
同名 actionName 启动失败
```

否则 AOP 代理后注解可能丢失。

## 5. P1：Try 异常语义

规划必须明确：

```text
branch.tryAction 返回 FAILURE -> rollback
branch.tryAction 抛异常 -> report TRY_FAIL + rollback
不把网络异常当成功
```

## 6. P1：客户端能力

```text
连接复用
deadline
统一错误码
discovery 失败降级/直连
```

否则默认客户端会成为新的瓶颈。

## 7. P1：开源门面

```text
LICENSE
CONTRIBUTING
README quickstart
examples
API javadoc
版本兼容说明
```

## 8. 结论

```text
C-004.1 先做公共模型 + DefaultTccTransactionTemplate
C-004.2 再拆 proto/client
C-004.3 再做 @TccAction + 通用 /tcc/try
C-004.4 再做 TCC Starter
C-004.5 最后迁 examples
```
