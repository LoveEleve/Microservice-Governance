# Poly 文档总览

> Poly 是五个独立的 Spring 服务治理工程：多活区域路由、配置统一抽象、动态多数据源、Redis 多机房复制、端点级网关路由。
> 源码：仓库根目录 [`poly/`](../../poly)；文档：本目录；来源：历史归档（2026-10-26 重组）。

## 项目与文档

| 项目 | 一句话定位 | 文档 | 源码 |
|---|---|---|---|
| **PolyZone** | Zone 感知路由：定位 → 同区域优先 → 事件联动 → 配置动态切换 | [README](polyzone/README.md) · 6 篇 | [`poly/polyzone`](../../poly/polyzone) |
| **PolyConfig** | 配置中心统一抽象：声明式 PropertySource + 属性级变更事件 + 后端适配 | [README](polyconfig/README.md) · 6 篇 + 学习指南 | [`poly/polyconfig`](../../poly/polyconfig) |
| **PolyData** | 动态多数据源：子上下文隔离、热替换、同区域优先、ShardingSphere | [README](polydata/README.md) · 8 篇 | [`poly/polydata`](../../poly/polydata) |
| **PolyGateway** | 端点级网关路由：服务发布端点元数据，网关生成精确 `lb://` 路由 | [README](polygateway/README.md) · 6 篇 + 学习指南 + L43 | [`poly/polygateway`](../../poly/polygateway) |
| **PolyCache** | Redis 多机房复制：Redisson 命令捕获 → Kafka → 语义重放 | [README](polycache/README.md) · 8 篇 | [`poly/polycache`](../../poly/polycache) |

## 建议阅读顺序

```
PolyZone          区域/多活路由，服务治理的直接切面
→ PolyConfig      配置变更事件，PolyData 的前置
→ PolyData        同时消费 Zone + Config，综合度最高
→ PolyGateway     网关侧治理，独立成线
→ PolyCache       数据面多机房复制，独立成线
→ 对比文档        Poly vs Microsphere，理解两套实现取舍
```

- **PolyData 依赖 PolyZone + PolyConfig**，不建议先读；
- PolyGateway / PolyCache 相对独立，可按兴趣穿插；
- 每个项目先读 `README.md`，再按编号读正文；`study-guide` 是索引/自测，不是正文。

## 对照阅读

- [Poly vs Microsphere](comparison/poly-vs-microsphere.md)：同一个服务治理问题，两套实现的设计轴对比。
- 建议配对：
  - PolyZone ↔ `microsphere-multiactive`
  - PolyConfig ↔ `microsphere-configuration` / `dynamic`
  - PolyData ↔ `microsphere-druid` / `microsphere-mybatis`
  - PolyGateway ↔ `microsphere-gateway`
  - PolyCache ↔ `microsphere-redis`
