# Microservice Governance

Microsphere 服务治理源码学习快照（2026-10-25）。

> 来源：https://github.com/microsphere-projects
> 本仓库为工作树快照，不含各子项目的 `.git` 历史、`target/`、`build/` 等构建产物；原始仓库请以 GitHub 为准。

## 目录

### 服务治理核心

- `spring-cloud` — `microsphere-spring-cloud`：多注册中心、Union Discovery、服务注册事件、OpenFeign 动态刷新、负载均衡
- `microsphere-nacos` — Nacos 集成
- `microsphere-configuration` — 配置扩展
- `dynamic` — 动态配置/动态化能力
- `microsphere-gateway` — 动态路由与服务发现接入
- `sentinel` — `microsphere-alibaba-sentinel`：限流/熔断集成
- `microsphere-resilience4j` — Resilience4j 扩展
- `microsphere-multiactive` — 多活/Zone 路由

### Spring / 基础

- `microsphere-java` — 公共 Java 能力
- `microsphere-spring` — Spring Framework 扩展
- `spring-boot` — `microsphere-spring-boot`：自动配置/Actuator/配置绑定
- `microsphere-bom` — 依赖 BOM
- `microsphere-build` — 父 POM 与构建配置

### 可观测与数据访问

- `observability` — `microsphere-observability`
- `logging` — `microsphere-logging`
- `microsphere-redis` — Redis 扩展
- `microsphere-mybatis` — MyBatis 扩展
- `microsphere-druid` — `microsphere-alibaba-druid`

### 工具与历史

- `microsphere-tools` — 工具类
- `java-enterprise` — `microsphere-java-enterprise`（历史 Java EE 线）
