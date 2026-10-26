# Microsphere BOM — 用户文档

> The English version is [here](../README.md).

本用户文档面向 BOM 制品的**使用者**，依据 `release` 分支（版本线 `0.3.x`，最新已发布版本 **0.3.16**）编写：
涵盖每个 Maven 模块的导入方式、托管的依赖版本、BOM 的"API"解释、代码示例以及 JDK 兼容范围。

## 什么是 BOM？

Bill of Materials（物料清单，BOM）是一种 `pom` 打包类型的 Maven 制品，其 `<dependencyManagement>` 段落固定了
第三方库的版本。导入 BOM 后，声明依赖时**无需写版本号**：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.microsphere-projects</groupId>
            <artifactId>microsphere-all-bom</artifactId>
            <version>0.3.16</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- 无需 <version/>，版本由 BOM 解析 -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
    </dependency>
</dependencies>
```

## 模块一览

| 模块 | 类型 | 用途 | 文档 |
|------|------|------|------|
| `microsphere-all-bom` | BOM（聚合） | 一站式导入全部领域 BOM | [all-bom.md](all-bom.md) |
| `microsphere-logging-bom` | BOM | 日志：SLF4J、Logback、Log4j、Log4j2、Commons Logging | [logging-bom.md](logging-bom.md) |
| `microsphere-testing-bom` | BOM | 测试：JUnit 4/5、Mockito、Hamcrest、JMH、JSONassert | [testing-bom.md](testing-bom.md) |
| `microsphere-javadb-bom` | BOM | 嵌入式数据库：SQLite JDBC、H2 | [javadb-bom.md](javadb-bom.md) |
| `microsphere-javaee-bom` | BOM | Java EE `javax.*` API 制品 | [javaee-bom.md](javaee-bom.md) |
| `microsphere-tomcat-bom` | BOM | Apache Tomcat embed 与完整库 | [tomcat-bom.md](tomcat-bom.md) |
| `microsphere-bom-example` | JAR（内部） | 验证 BOM 完整性的示例工程（代码示例参考其测试用例） | [bom-example.md](bom-example.md) |

## 阅读建议

1. **只想为常用库获得合理版本？** → 导入 [`microsphere-all-bom`](all-bom.md)。
2. **只需要某一个技术领域？** → 导入对应领域 BOM（logging / testing / javadb / javaee / tomcat）。
3. **我的 JDK 应该用哪个版本？** → 见 [版本与兼容性](versions.md)：包含发布历史、按 JDK 激活的版本规则
   （Maven profile）以及每个托管库的兼容矩阵。

## 关键信息（release / 0.3.16）

| 项目 | 值 |
|------|-----|
| 所有制品的 GroupId | `io.github.microsphere-projects` |
| 最新已发布版本 | `0.3.16`（分支 HEAD 为 `0.3.17-SNAPSHOT`） |
| CI 验证的 JDK | 8、11、17、21、25（Eclipse Temurin） |
| 父构建 POM | `io.github.microsphere-projects:microsphere-build` |
| 许可证 | Apache License 2.0 |

## 相关文档

- 全部文档索引：[docs/zh/README.md](../../../README.md)
- 开发者文档（架构与源码分析）：[../developer-guide/zh/README.md](../../developer-guide/zh/README.md)
