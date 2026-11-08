# TCC Nacos 接入

> 读完你能做什么：知道 Coordinator 如何注册 gRPC 实例到 Nacos，以及为什么不用 Spring Cloud 默认 Web 注册。

## 1. 为什么不用默认 discovery

Spring Cloud Nacos 默认按 Web 端口注册实例；Coordinator 只有 gRPC server，没有 Web 容器。

如果直接开启默认 discovery：

```text
没有 WebServerInitializedEvent
-> 不会注册
```

所以使用专用注册器。

## 2. 注册器

```text
TccCoordinatorNacosRegistrar
  ApplicationReadyEvent -> register
  destroy -> namingService.shutDown

Instance:
  ip
  grpcPort
  serviceName
  group
  cluster
  metadata.protocol=grpc
```

## 3. 配置

```yaml
spring:
  application:
    name: crm-tx-tcc-coordinator
  cloud:
    nacos:
      discovery:
        enabled: false
        server-addr: 127.0.0.1:8848
      config:
        enabled: ${NACOS_CONFIG_ENABLED:false}

crm:
  tx:
    tcc:
      coordinator:
        nacos:
          enabled: true
          group: DEFAULT_GROUP
          cluster: DEFAULT
        grpc-port: 9090
```

## 4. 为什么默认关闭

```text
本地测试不需要 Nacos
CI 不启动 Nacos
生产通过环境变量/配置打开
```

## 5. 服务发现边界

Nacos 只注册 Coordinator：

```text
TC -> Nacos
  serviceName = crm-tx-tcc-coordinator
  metadata.protocol = grpc

TC 不查 Nacos 找 RM
RM 的 endpoint 仍由 branch register 提供
```

## 6. 当前限制

```text
没有真实 Nacos 集群验证
没有灰度权重
没有 config 热更新测试
没有 Sentinel
```

## 7. 一句话

> **Coordinator 用专用 Registrar 按 gRPC 端口注册，避免 Spring Cloud 默认 Web 端口注册失效。**
