# Microsphere BOM — 开发者文档

> The English version is [here](../README.md).

本开发者文档面向 `microsphere-bom` 项目（`release` 分支，版本线 `0.3.x`）的**维护者与贡献者**，
解释架构设计、逐模块分析 POM "源码"、总结所用到的 Maven 设计模式，并用流程图呈现复杂流程。

> 说明：本仓库几乎没有 Java 代码 —— 它的"源码"就是一组 POM 文件。唯一的 Java 源码位于
> `microsphere-bom-example`，作为所有 BOM 的构建期完整性校验。

## 章节

| 章节 | 主题 |
|------|------|
| [architecture.md](architecture.md) | 总体架构、模块依赖关系图、Reactor 构建顺序（含流程图） |
| [build-system.md](build-system.md) | `${revision}` ci-friendly 版本方案、flatten 插件、父工程 `microsphere-build`、JDK profile 机制（含流程图） |
| [ci-cd.md](ci-cd.md) | GitHub Actions 工作流：构建矩阵、发布流程、分支同步、Release Notes 生成（含流程图） |
| [logging-bom.md](logging-bom.md) | `microsphere-logging-bom` 源码分析 |
| [testing-bom.md](testing-bom.md) | `microsphere-testing-bom` 源码分析 |
| [javadb-bom.md](javadb-bom.md) | `microsphere-javadb-bom` 源码分析 |
| [javaee-bom.md](javaee-bom.md) | `microsphere-javaee-bom` 源码分析 |
| [tomcat-bom.md](tomcat-bom.md) | `microsphere-tomcat-bom` 源码分析 |

## 你将了解

- **架构设计** —— 为什么项目拆分为一个聚合器（`microsphere-all-bom`）、五个领域 BOM、一个父 POM 与一个校验模块，以及它们之间的依赖关系。
- **源码分析** —— 逐段解读每个 `pom.xml`：properties、`dependencyManagement`、BOM import 与按 JDK 激活的 profile。
- **设计模式** —— Bill of Materials 模式、`import` 作用域的依赖管理、Aggregator（reactor）POM、属性间接化（`<xxx.version>`）、
  profile 环境激活、ci-friendly `${revision}` + flatten-maven-plugin，以及 Maven 依赖管理中"先声明者优先"的优先级规则。
- **流程图** —— 按 JDK 的版本决议流程、发布流水线（`release` → 打 tag → Maven Central → 自动升版 → 合并回 `main`）与 dependabot 维护流程。

## 如何构建本项目

```bash
# 完整构建（全部模块，microsphere-bom-example 中运行测试）
./mvnw clean verify

# 单模块构建
./mvnw clean verify -pl microsphere-testing-bom

# 模拟特定 JDK 线构建（profile 依据*构建时* JDK 激活）
./mvnw clean verify -Djava.version=17
```

## 贡献检查清单

新增或修改托管依赖时（参见 [architecture.md](architecture.md#adding-a-managed-dependency)）：

1. 在所属领域 BOM 中新增/调整 `<xxx.version>` 属性。
2. 在该模块的 `<dependencyManagement>` 中新增条目。
3. 若该库需要按 JDK 区分版本，新增或更新带激活范围的 profile。
4. `microsphere-all-bom` 无需修改（它导入的是整个 BOM 而非单个制品）。
5. 在 JDK 8/11/17/21/25 上运行 `./mvnw clean verify`（或交由 CI 验证）。

## 相关文档

- 全部文档索引：[docs/README.md](../../../README.md)
- 用户文档：[../user-guide/zh/README.md](../../user-guide/zh/README.md)
