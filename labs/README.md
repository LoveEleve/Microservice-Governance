# Labs

服务治理组件的**用法展示工程**：只追求代码准确、结构清晰，不要求启动；需要真实中间件时在各自 README 中说明。

## 当前

| Lab | 对应能力 | 说明 |
|---|---|---|
| [`microsphere-spring-cloud-lab`](microsphere-spring-cloud-lab/README.md) | Multiple Service Registry / Union Discovery / Registration Events / OpenFeign Auto-Refresh | 两个内存注册中心 + 两个内存 DiscoveryClient，不依赖真实 Nacos/Eureka |
| [`redisson-lab`](redisson-lab/README.md) | RLock / tryLock / watchdog / fair / RW / multi / fenced / semaphore / latch | Redisson Book 配套使用工程；需要一个 Redis，不默认启动 |
| [`seata-at-multiservice-lab`](seata-at-multiservice-lab/README.md) | 三服务、三数据源、AT、XID 透传、`@GlobalTransactional` | Seata 小册第 2 章配套；需要 3 个 MySQL 库 + Seata Server，不默认启动 |
| [`seata-tcc-lab`](seata-tcc-lab/README.md) | 两服务、两数据源、Seata TCC、`@LocalTCC`、fence 表、XID 透传 | Seata 小册第 4 章配套；需要 2 个 MySQL 库 + Seata Server，不默认启动 |
| [`seata-saga-lab`](seata-saga-lab/README.md) | 编排服务 + 机票/酒店/租车三服务、状态机、补偿链 | Seata 小册第 5 章配套；不依赖 MySQL，默认不启动 |

## 约定

- 每个 Lab 一个独立 Maven 工程；
- 只写“怎么用 + 为什么这样接线”，不复制框架源码；
- 真实运行所需的外部依赖（Nacos、Eureka、Redis 等）在 README 中列明，不默认启动。
