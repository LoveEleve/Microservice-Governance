# Microsphere 文档总览

> 小马哥 Microsphere 服务治理源码学习资料。
> 来源：历史归档的 `xmg-stage` + `microsphere-source`（2026-10-26 重组）。
> 原则：**宏观总览、L3 章节、源码映射、源码大纲、方法论、历史进度分层存放，不互相混放。**

## 目录

| 目录 | 内容 | 用法 |
|---|---|---|
| `00-overview/` | L3 总览/骨架、L2 跨仓库知识图谱、方向规划、课程侧 4 篇完整梳理、[技术评审方法](00-overview/技术评审方法.md)、[多注册中心设计对比](00-overview/多注册中心设计对比.md) | 先读，建立地图与评审口径 |
| `01-dimensions/` | L3 的 5 个维度、33 个主题章节（规范/分布式问题/理论/工程/性能） | 主线正文 |
| `02-source-mapping/` | 14 个仓库的逐仓库映射表 | 找知识点 → 源码位置 |
| `03-source-outline/` | 14 个仓库的知识大纲 | 仓库级学习提纲 |
| `04-method/` | 现代实现映射与自主落地方法论 | 决定“学到什么程度” |
| `05-progress/` | 历史 HANDOVER 交接记录 | 只作进度参考，不是正文 |
| `06-production/` | [生产化缺口清单](06-production/生产化缺口清单.md) | 边学边关闭的 backlog |

## 建议入口

- [00-系列规划（55 主线 + 9 附录 = 64 篇）](00-系列规划.md)

先读 [技术评审方法](00-overview/技术评审方法.md)：以“问题 → 官方 → Microsphere → 边界 → 决策”为固定口径，不把 Microsphere 当正确性标准。

```
00-overview/L3总览终稿.md
  → 00-overview/course/stage-1-服务治理-完整梳理.md（宏观服务治理地图）
  → 03-source-outline/05-microsphere-spring-cloud-服务治理知识大纲.md
  → 02-source-mapping/05-microsphere-spring-cloud.md
  → 源码 microsphere-spring-cloud
```

第一个要攻的主线是 **`microsphere-spring-cloud`**：

1. Multiple Service Registry
2. Union Discovery
3. Service Registration Events
4. OpenFeign Auto-Refresh
5. 负载均衡工具

## 边界

- `01-confucius-commons` 是 2017 年旧仓库；保留在映射/大纲中仅作历史参考，不进入主线。
- 旧的 `microsphere-analysis/`（18 仓旧分析）**未纳入**：方法论讨论已判定其非权威，避免与 `microsphere-source` 两套口径混放。
- `05-progress/` 是历史交接快照，不能替代 `00-overview`/`01-dimensions`。
- 本仓库只保留技术内容；历史归档原文如出现旧项目字样，仅代表来源，不作为学习对象或实现目标。
- Poly 文档在 [`docs/poly/`](../poly/README.md)，与 Microsphere 严格分区；对照阅读见 [`poly/comparison/poly-vs-microsphere.md`](../poly/comparison/poly-vs-microsphere.md)。
