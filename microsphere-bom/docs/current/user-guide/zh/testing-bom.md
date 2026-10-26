# microsphere-testing-bom — 用户文档

> 本文属于 [Microsphere BOM 用户文档](README.md) · 英文版见 [../testing-bom.md](../testing-bom.md)

## 概述

`microsphere-testing-bom` 集中管理测试与基准测试库的版本：JUnit 4、JUnit 5 / Jupiter（经由官方 `junit-bom`）、
Mockito（经由 `mockito-bom`）、Hamcrest、JMH 与 JSONassert。本仓库自身的测试（`ExampleServiceTest`）使用的正是
这个 BOM。

## Maven 坐标

| 字段 | 值 |
|------|-----|
| groupId | `io.github.microsphere-projects` |
| artifactId | `microsphere-testing-bom` |
| version | `0.3.16`（release 版本线 `0.3.x`） |
| packaging | `pom`（BOM） |

## API 解释

- 该 BOM **内嵌了两个上游 BOM**（`org.junit:junit-bom` 与 `org.mockito:mockito-bom`，均以
  `<type>pom</type><scope>import</scope>` 导入）。因此受版本管理的是整个 Jupiter 与 Mockito **制品族**，
  而不仅是显式列出的几个坐标。
- 按 JDK 激活的 profile `java8-16` 在 JDK `[1.8, 17)` 上降级 Jupiter 与 Mockito（JUnit 6 与 Mockito 5 要求更高 JDK）。
- 托管版本以属性暴露（`junit.version`、`junit-jupiter.version`、`mockito.version` 等）；项目内覆盖请使用
  **"先声明者优先"** 方式（机制同 [logging-bom.md](logging-bom.md) 中的覆盖示例）。

## 托管依赖清单

| GroupId | ArtifactId | 版本属性 | 默认（JDK 17+） | JDK 覆盖 |
|---------|------------|----------|------------------|----------|
| `junit` | `junit` | `junit.version` | 4.13.2 | — |
| `org.junit` | `junit-bom`（import） | `junit-jupiter.version` | **6.1.3** | JDK `[1.8, 17)` 时为 5.14.4 |
| `org.mockito` | `mockito-bom`（import） | `mockito.version` | **5.24.0** | JDK `[1.8, 17)` 时为 4.11.0 |
| `org.skyscreamer` | `jsonassert` | `jsonassert.version` | 1.5.3 | — |
| `org.hamcrest` | `hamcrest` | `hamcrest.version` | 3.0 | — |
| `org.openjdk.jmh` | `jmh-core` | `jmh.version` | 1.37 | — |
| `org.openjdk.jmh` | `jmh-generator-annprocess` | `jmh.version` | 1.37 | — |

由于导入了 `junit-bom`，`junit-jupiter`、`junit-jupiter-api`、`junit-jupiter-engine`、`junit-jupiter-params`、
`junit-platform-*` 等坐标均自动被解析；`mockito-bom` 同理覆盖 `mockito-core`、`mockito-junit-jupiter`、
`mockito-inline` 等。

## 代码示例

以下就是 `microsphere-bom-example` 工程使用的模式 —— 其 pom 导入 `microsphere-all-bom` 后以**无版本**方式声明
JUnit Jupiter：

```xml
<!-- microsphere-bom-example/pom.xml（节选） -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.microsphere-projects</groupId>
            <artifactId>microsphere-all-bom</artifactId>
            <version>${revision}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

测试代码取自 `ExampleServiceTest.java`（本仓库的参考测试用例）：

```java
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ExampleServiceTest {

    private final ExampleService service = new ExampleService();

    @Test
    public void testGreet() {
        assertEquals("Hello, World!", service.greet("World"));
    }

    @Test
    public void testGreetWithNullName() {
        assertThrows(IllegalArgumentException.class, () -> service.greet(null));
    }

    @Test
    public void testGetJavaVersion() {
        assertNotNull(service.getJavaVersion());
    }
}
```

Mockito 同样无需写版本（经 `mockito-bom` 解析）：

```xml
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

## 版本信息与兼容性

- **BOM 版本**：版本线 `0.3.x`（最新 **0.3.16**）。
- **JDK 兼容性**：

| 库 | 默认版本线 | 最低 JDK | JDK 8–16 回退 | 原因 |
|----|------------|--------------------------|---------------|------|
| JUnit Jupiter | 6.1.3 | Java 17 | 5.14.4 | JUnit 6 要求 Java 17+ |
| Mockito | 5.24.0 | Java 11 | 4.11.0 | Mockito 5 不再支持 Java 8；4.11.0 是最后的 Java 8 兼容版本线 |
| JUnit 4 | 4.13.2 | Java 5+ | — | 各 JDK 不变 |
| Hamcrest 3.0 | 3.0 | Java 8 | — | |
| JMH 1.37 | 1.37 | Java 8 | — | |
| JSONassert 1.5.3 | 1.5.3 | Java 7+ | — | |

- **Profile**：`java8-16`（JDK `[1.8, 17)` 自动激活）→ `junit-jupiter.version=5.14.4`、`mockito.version=4.11.0`。
- **CI 验证**：整个 reactor（含本 BOM 与 `microsphere-bom-example`）在 JDK 8、11、17、21、25（Temurin）上构建并运行测试。

## 相关文档

- 开发者源码分析：[../../developer-guide/zh/testing-bom.md](../../developer-guide/zh/testing-bom.md)
- 版本总览：[versions.md](versions.md)
