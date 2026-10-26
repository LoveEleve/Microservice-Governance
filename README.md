# Microservice Governance

Microsphere + Poly 服务治理源码学习快照（2026-10-26）。

> 来源：https://github.com/microsphere-projects ；Poly 来自 /data/workspace/source-code/poly。
> 本仓库为工作树快照，不含各子项目的 `.git` 历史、`target/`、`build/` 等构建产物；原始仓库请以 GitHub 为准。

## 目录

### Poly（多活/治理扩展，独立工程）

- `poly/polyzone` — 区域感知路由：Zone 定位 → 同区域优先 → 事件联动 → 配置动态切换
- `poly/polyconfig` — 配置中心统一抽象：声明式 PropertySource + 属性级变更事件 + 后端适配器
- `poly/polydata` — 动态多数据源：子上下文隔离、热替换、同区域优先、ShardingSphere
- `poly/polycache` — Redis 多机房复制：Redisson 命令拦截 → Kafka → 语义重放
- `poly/polygateway` — 端点级网关路由：服务发布端点元数据，网关生成精确 `lb://` 路由

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
