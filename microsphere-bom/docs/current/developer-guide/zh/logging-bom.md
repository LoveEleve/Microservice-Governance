# microsphere-logging-bom — 源码分析

> 本文属于 [Microsphere BOM 开发者文档](README.md) · 英文版见 [../logging-bom.md](../logging-bom.md)

模块源码：[`microsphere-logging-bom/pom.xml`](../../../../microsphere-logging-bom/pom.xml)

## 1. POM 骨架

```
<parent>       microsphere-bom:${revision}          ← 仅继承构建配置，无托管依赖下沉
<properties>   5 个 *.version                        ← 唯一的版本编写点
<dependencyManagement>  8 条目，<version> 全部经 ${...} 间接化
<profiles>     java8-10                             ← 覆盖属性，而非覆盖依赖条目
```

## 2. properties 段

```xml
<properties>
    <commons-logging.version>1.4.0</commons-logging.version>
    <slf4j.version>2.0.20</slf4j.version>
    <logback.version>1.6.5</logback.version>   <!-- 三个制品共享这一旋钮 -->
    <log4j.version>1.2.17</log4j.version>      <!-- Log4j 1.x，为遗留保留 -->
    <log4j2.version>2.26.1</log4j2.version>    <!-- 两个制品共享这一旋钮 -->
</properties>
```

此处执行的约定：

- 每个属性以 `.version` 结尾，前缀为制品（或制品族）名。
- **按"版本决策"而非"制品"设属性**：`logback.version` 同时驱动 `logback-classic`、`logback-core`、
  `logback-access` —— 它们同列车发布，绝不允许错配。

## 3. dependencyManagement 分析

| 条目 | 类型 | 理由 |
|------|------|------|
| `commons-logging:commons-logging` | 直接钉版 | 桥接/遗留 API 仍会引入它 |
| `org.slf4j:slf4j-api` | 直接钉版 | 只管理门面；**有意不管理** `slf4j-log4j12` 等适配器，因为 SLF4J 2.x 的 provider 就是 Logback/Log4j2 本身 |
| `ch.qos.logback:logback-{classic,core,access}` | 制品族 | classic 依赖同版本 core；三者齐管防止错配 |
| `log4j:log4j` | 直接钉版 | 1.2.17 终版；EOL 制品，让遗留传递依赖落在已知版本 |
| `org.apache.logging.log4j:log4j-{api,core}` | 制品族 | 同 Logback 的同列车规则 |

本模块没有 `<type>pom</type><scope>import</scope>` 条目 —— 它直接编写原始版本（不同于嵌套上游 BOM 的
[testing-bom](testing-bom.md)）。

## 4. Profile：`java8-10`

```xml
<profile>
    <id>java8-10</id>
    <activation><jdk>[1.8,11)</jdk></activation>
    <properties>
        <logback.version>1.3.16</logback.version>
    </properties>
</profile>
```

设计要点：

- **属性覆盖模式**：profile 改的是*属性*而非托管依赖条目，`dependencyManagement` 块保持与版本无关 —— 这是
  今后每一处 JDK 分叉应沿用的模式。
- 范围 `[1.8,11)` 恰好覆盖 Logback 1.5/1.6（Java 11 字节码）无法加载的 JDK；1.3.16 是 Logback Java-8 线的终点版本。
- 边界语义：JDK `< 1.8` **不**激活该范围（保留基础值 1.6.5）—— 有意为之，本项目的支持下限是 Java 8
  （见 [versions.md](../../user-guide/zh/versions.md)）。

```mermaid
flowchart TD
    A[解析 logback.version] --> B{JDK 在 [1.8,11)？}
    B -- 是 --> C[profile java8-10 激活<br/>logback.version=1.3.16]
    B -- 否 --> D[基础 logback.version=1.6.5]
    C & D --> E[内插到 logback-classic/core/access]
```

## 5. 演进与维护

- 依赖升级由 Dependabot 对 `main` 开 PR → 合并 → 同步到 `release`（见 [ci-cd.md](ci-cd.md)）；这类 PR 应只改动
  版本属性行。
- 当 Logback 要求的基线高于当前默认值时，应**新增 profile**（如 `java11-16`）而不是改基础值 —— 基础值必须始终
  是*最新* JDK 的答案。

## 相关文档

- 消费者契约：[../../user-guide/zh/logging-bom.md](../../user-guide/zh/logging-bom.md)
- 模式目录：[architecture.md](architecture.md#6-设计模式)
