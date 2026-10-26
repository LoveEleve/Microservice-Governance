# 版本与兼容性

> 本文属于 [Microsphere BOM 用户文档](README.md) · 英文版见 [../versions.md](../versions.md)

本页是 `release` 分支（版本线 `0.3.x`）下**应使用哪个 BOM 版本、解析出哪些库版本、支持哪些 JDK 范围**的唯一权威参考。

## BOM 发布信息

| 项目 | 值 |
|------|-----|
| groupId（全部模块） | `io.github.microsphere-projects` |
| 最新已发布版本 | **0.3.16** |
| `release` 分支 HEAD | `0.3.17-SNAPSHOT`（发布后自动升版） |
| 版本线 | `0.3.x`（此前为 `0.1.x`、`0.2.x`） |
| 7 个模块共享同一版本 | 同一 reactor 一起发布；切勿混用不同发布版本的 BOM |
| 父构建 POM | `io.github.microsphere-projects:microsphere-build` |
| 许可证 | Apache License 2.0 |

发布历史维护在仓库根目录的 [`release-notes.md`](../../../../release-notes.md) 及
[GitHub Releases](https://github.com/microsphere-projects/microsphere-bom/releases)。

## 受支持的 JDK 矩阵（CI 验证）

| JDK 8 | JDK 11 | JDK 17 | JDK 21 | JDK 25 |
|-------|--------|--------|--------|--------|
| ✅ Temurin | ✅ Temurin | ✅ Temurin | ✅ Temurin | ✅ Temurin |

CI（`.github/workflows/maven-build.yml`）在每个上述 JDK 上构建整个 reactor 并运行 `ExampleServiceTest`。

## 按 JDK 的有效版本

由于发布后的 BOM 保留了 `<profiles>`，下表版本是在**你的构建**中按执行 Maven 的 JDK 解析出来的：

| 库 / 制品族 | JDK ≤ 7 ¹ | JDK 8 | JDK 9–10 | JDK 11–16 | JDK ≥ 17 |
|--------------|-----------|-------|----------|-----------|----------|
| Logback（logging-bom） | 1.6.5 | **1.3.16**（`java8-10`） | **1.3.16**（`java8-10`） | 1.6.5 | 1.6.5 |
| JUnit Jupiter（testing-bom） | 6.1.3 | **5.14.4**（`java8-16`） | **5.14.4** | **5.14.4** | 6.1.3 |
| Mockito（testing-bom） | 5.24.0 | **4.11.0**（`java8-16`） | **4.11.0** | **4.11.0** | 5.24.0 |
| H2（javadb-bom） | **1.4.200**（`java8`） | **1.4.200**（`java8`） | 2.5.252 | 2.5.252 | 2.5.252 |
| Tomcat（tomcat-bom） | **9.0.120**（`java8`） | **9.0.120**（`java8`） | **9.0.120** | **10.1.57**（`java11+`） | **11.0.24**（`java17+` 覆盖 `java11+`） |
| 其余（SLF4J、Log4j/Log4j2、JUnit 4、Hamcrest、JMH、JSONassert、SQLite、javax.* API） | 各 JDK 一致 | | | | |

¹ 仅为完整性列出：JDK ≤ 7 不会激活任何 profile（激活范围均从 `1.8` 起），Maven 会保留基础属性值，尽管
Jupiter 6 / Mockito 5 / Logback 1.6 无法在该运行时上工作。本 BOM 版本线的支持下限是 **JDK 8**。

## 各库兼容性说明

| 库 | 托管版本 | 该版本最低 JDK | 说明 |
|----|----------|----------------|------|
| SLF4J | 2.0.20 | 8 | 2.x 的 ServiceLoader 提供者机制 |
| Logback | 1.6.5 / 1.3.16 | 11 / 8 | 1.5+ 要求 Java 11；1.3.x 为 Java 8 版本线 |
| Log4j2 | 2.26.1 | 8 | |
| Log4j 1.x | 1.2.17 | 5 | 已 EOL，仅为遗留兼容保留 |
| Commons Logging | 1.4.0 | 8 | |
| JUnit Jupiter | 6.1.3 / 5.14.4 | 17 / 8 | JUnit 6 要求 Java 17 |
| Mockito | 5.24.0 / 4.11.0 | 11 / 8 | Mockito 5 不再支持 Java 8 |
| JUnit 4 | 4.13.2 | 5 | |
| Hamcrest | 3.0 | 8 | |
| JMH | 1.37 | 8 | |
| JSONassert | 1.5.3 | 7 | |
| SQLite JDBC | 3.53.4.0 | 8 | |
| H2 | 2.5.252 / 1.4.200 | 8 / 7 | 1.4.x 与 2.x 在方言/存储格式上有差异 |
| javax.* Java EE API | 见 [javaee-bom.md](javaee-bom.md) | Java 6/7/8 字节码 | Java EE 8 命名空间；Jakarta EE 9+ 使用 `jakarta.*` |
| Apache Tomcat | 11.0.24 / 10.1.57 / 9.0.120 | 17 / 11 / 8 | 9.0 为 `javax.servlet`，10.1+/11 为 `jakarta.servlet` |

## 升级指引

- **0.2.x → 0.3.x**：模块布局不变；库版本线整体前移（如 Jupiter 默认 5.x → 6.x、Tomcat 11）。若仍在 Java 8 上构建，
  请复核 [testing-bom](testing-bom.md) 的 JDK 回退规则。
- 需要无视 JDK 固定某个库时，使用 [logging-bom.md](logging-bom.md) 描述的**"先声明者优先"**覆盖法。
- Dependabot 持续刷新托管库版本；每次升级落在补丁发布（`0.3.n`）。发布机制见
  [../developer-guide/zh/ci-cd.md](../../developer-guide/zh/ci-cd.md)。

## 相关文档

- 各模块文档：[logging](logging-bom.md) · [testing](testing-bom.md) · [javadb](javadb-bom.md) ·
  [javaee](javaee-bom.md) · [tomcat](tomcat-bom.md) · [all](all-bom.md) · [example](bom-example.md)
- profile 机制内部原理（开发者）：[../developer-guide/zh/build-system.md](../../developer-guide/zh/build-system.md)
