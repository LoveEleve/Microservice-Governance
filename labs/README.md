# Labs

服务治理组件的**用法展示工程**：只追求代码准确、结构清晰，不要求启动；需要真实中间件时在各自 README 中说明。

## 当前

| Lab | 对应能力 | 说明 |
|---|---|---|
| [`microsphere-spring-cloud-lab`](microsphere-spring-cloud-lab/README.md) | Multiple Service Registry / Union Discovery / Registration Events / OpenFeign Auto-Refresh | 两个内存注册中心 + 两个内存 DiscoveryClient，不依赖真实 Nacos/Eureka |

## 约定

- 每个 Lab 一个独立 Maven 工程；
- 只写“怎么用 + 为什么这样接线”，不复制框架源码；
- 真实运行所需的外部依赖（Nacos、Eureka、Redis 等）在 README 中列明，不默认启动。
