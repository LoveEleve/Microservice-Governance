# Poly

> Five independent Spring projects: multi-zone routing, unified configuration, dynamic datasources, Redis multi-datacenter replication and endpoint-level gateway routing.
> 五个独立的 Spring 项目：多活区域路由、配置中心统一抽象、动态多数据源、Redis 多机房复制、端点级网关路由。

## 项目一览

| 项目 | 定位 | 基线 | 测试 |
|------|------|------|------|
| [polyzone](./polyzone) | 区域感知路由：四级区域定位 → 同区域优先过滤（三级安全阀）→ 事件联动 → 配置中心动态切换 | Spring Boot 3.2 / Spring Cloud 2023.0.3 | 132 |
| [polyconfig](./polyconfig) | 配置中心统一抽象：声明式 `@PropertySource` 扩展 + 属性级变更事件（名 + 新旧值 + 来源）+ 后端适配器模板 | Spring Boot 3.2 | 24 |
| [polydata](./polydata) | 动态多数据源与分库分表：子上下文隔离、热替换、同区域优先/故障转移、ORM 同进程并存、ShardingSphere YAML 直构 | Spring Boot 3.2 / ShardingSphere 5.5.1 | 80 |
| [polycache](./polycache) | Redis 多机房复制：Redisson 命令级无侵入拦截 → 写命令过滤 → Kafka 分区 → 语义重放（防回环 / 至少一次 / 分区内保序），含动态连接路由与拦截器 SPI | Spring Boot 3.2 / Redisson 3.27 / Kafka | 60 |
| [polygateway](./polygateway) | 端点级网关路由（Spring Cloud Gateway WebFlux）：服务发布端点元数据，网关生成 `lb://` 精确路由（官方谓词 + 响应式负载均衡，零阻塞） | Spring Cloud Gateway 4.1 | 39 |

合计 **335 个测试**（H2 纯内存 / Embedded Kafka / 3 节点 Redis 真集群 / Docker 双机房演示）。

## 构建

```bash
# 聚合构建全部（按依赖顺序，polydata 的 SNAPSHOT 依赖由同一 reactor 解析）
mvn clean test

# 单独构建某个项目
mvn -f polyzone/pom.xml clean test

# polydata 依赖 polyzone 与 polyconfig 的 1.0.0-SNAPSHOT，单独构建前先安装两者
mvn -f polyzone/pom.xml clean install
mvn -f polyconfig/pom.xml clean install
mvn -f polydata/pom.xml clean test
```

每个子目录都是自包含的多模块工程（自己的父 pom、README、LICENSE、NOTICE、`mvnw`），可以单独 clone、单独使用。

## License

Apache License, Version 2.0，详见各子目录的 `LICENSE` 与 `NOTICE`。
