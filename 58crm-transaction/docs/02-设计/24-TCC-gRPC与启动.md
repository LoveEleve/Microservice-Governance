# TCC gRPC 与启动

> 读完你能做什么：知道 proto 如何生成 stub、Coordinator 如何把 gRPC 请求映射到核心服务，以及服务怎么启动。

## 1. 模块

```text
crm-tx-tcc-api
  tcc.proto
  protobuf-maven-plugin
  grpc stub

crm-tx-tcc-coordinator
  TccCoordinatorGrpcService
  TccCoordinatorGrpcServer
  TccCoordinatorApplication
  TccCoordinatorConfiguration
```

## 2. proto 编译

`crm-tx-tcc-api/pom.xml`：

```text
protobuf-maven-plugin 0.6.1
protoc 3.25.5
protoc-gen-grpc-java 1.83.1
os-maven-plugin 1.7.1
```

生成：

```text
target/generated-sources/protobuf/java
target/generated-sources/protobuf/grpc-java
```

生成类：

```text
com.wuba.crm.tx.tcc.api.grpc.BeginRequest
com.wuba.crm.tx.tcc.api.grpc.TccCoordinatorGrpc
...
```

## 3. gRPC adapter

`TccCoordinatorGrpcService`：

```text
Begin          -> TccCoordinatorService.begin
RegisterBranch -> TccCoordinatorService.registerBranch
ReportBranch   -> TccCoordinatorService.reportBranch
```

职责：

```text
proto -> API DTO
API result -> proto response
异常 -> ResultCode
```

不在 adapter 里：

```text
事务状态机
数据库访问
owner lease
```

## 4. 错误映射

```text
TccException.code -> proto ResultCode
IllegalArgumentException -> TCC_INVALID_ARGUMENT
未预期 RuntimeException -> TCC_INTERNAL_ERROR
```

新增错误码：

```text
TCC_INVALID_ARGUMENT
TCC_INTERNAL_ERROR
```

## 5. gRPC server

`TccCoordinatorGrpcServer`：

```text
NettyServerBuilder.forPort(port)
  .addService(adapter)
  .start()
```

端口：

```yaml
crm:
  tx:
    tcc:
      coordinator:
        grpc-port: 9090
```

生命周期：

```text
afterPropertiesSet -> start
destroy            -> shutdown / await 5s / shutdownNow
```

## 6. Spring 启动

入口：

```text
com.wuba.crm.tx.tcc.coordinator.TccCoordinatorApplication
```

配置：

```text
@MapperScan(
  basePackages = coordinator.persistence + tcc.id,
  annotationClass = Mapper.class
)
只扫描显式 @Mapper，避免把 Repository 接口注册成 Mapper
Clock UTC
MybatisSegmentAllocator
SegmentIdGenerator
TccXidGenerator
```

`MybatisSegmentAllocator` 必须由 Spring 容器创建，否则 `@Transactional` 不生效。

## 7. 数据库

启动前执行：

```text
crm-tx-tcc-coordinator/src/main/resources/db/tcc-schema.sql
crm-tx-tcc-id/src/main/resources/db/id-segment-schema.sql
```

环境变量：

```text
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD
TCC_GRPC_PORT
```

## 8. 测试

```text
TccCoordinatorGrpcServiceTest
  Begin 成功映射
  TccException 映射
  参数错误映射
  RegisterBranch 映射
  ReportBranch 成功/非法状态

TccCoordinatorGrpcServerTest
  端口 0 启动/关闭
```

## 9. 未实现

```text
Commit / Rollback / Query RPC
  -> C-003.3

TccParticipantCallback gRPC server
  -> participant SDK

TLS
鉴权
限流
```

## 10. 一句话

> **proto 只负责协议，adapter 只做映射，TccCoordinatorService 才是状态机；gRPC server 只负责端口和生命周期。**
