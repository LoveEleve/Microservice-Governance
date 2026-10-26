# microsphere-bom-example — 用户文档

> 本文属于 [Microsphere BOM 用户文档](README.md) · 英文版见 [../bom-example.md](../bom-example.md)

## 概述

`microsphere-bom-example` 是本仓库的**校验模块**：一个普通 JAR 工程，导入 `microsphere-all-bom`、以无版本方式
声明依赖，并在 CI 矩阵的每个 JDK（8、11、17、21、25）上真实编译并运行测试，用来证明整条 BOM 链路端到端可用。

该模块**不会发布到 Maven Central**（其 `maven-deploy-plugin` 配置了 `<skip>true</skip>`），也不作为依赖被消费 ——
它是"如何使用 BOM"的可执行参考示例。

## Maven 坐标

| 字段 | 值 |
|------|-----|
| groupId | `io.github.microsphere-projects` |
| artifactId | `microsphere-bom-example` |
| version | `0.3.16`（仅本地 reactor —— 已跳过 deploy） |
| packaging | `jar` |

## API 解释

模块包含一个业务类与一个测试类。

### `io.github.microsphere.bom.ExampleService`

| 方法 | 契约 | 异常 |
|------|------|------|
| `String greet(String name)` | 对任意非空 `name` 返回 `"Hello, <name>!"` | 当 `name` 为 `null` 或 `""` 时抛 `IllegalArgumentException` —— `"name must not be null or empty"` |
| `String getJavaVersion()` | 返回 JVM 属性 `java.specification.version`（如 `"17"`） | — （合规 JVM 上永不为 `null`） |

```java
// src/main/java/io/github/microsphere/bom/ExampleService.java（节选）
public String greet(String name) {
    if (name == null || name.isEmpty()) {
        throw new IllegalArgumentException("name must not be null or empty");
    }
    return "Hello, " + name + "!";
}

public String getJavaVersion() {
    return System.getProperty("java.specification.version");
}
```

### `io.github.microsphere.bom.ExampleServiceTest`

```java
// src/test/java/io/github/microsphere/bom/ExampleServiceTest.java（节选）
@Test
public void testGreet() {
    assertEquals("Hello, World!", service.greet("World"));
}

@Test
public void testGreetWithNullName() {
    assertThrows(IllegalArgumentException.class, () -> service.greet(null));
}

@Test
public void testGreetWithEmptyName() {
    assertThrows(IllegalArgumentException.class, () -> service.greet(""));
}

@Test
public void testGetJavaVersion() {
    assertNotNull(service.getJavaVersion());
}
```

测试类本身就是**测试 BOM 的演练**：`junit-jupiter` 以无版本方式声明，并在矩阵的每个 JDK 上运行 —— JDK
17/21/25 使用 JUnit 6.1.3，JDK 8/11 经由 [microsphere-testing-bom](testing-bom.md) 的 `java8-16` profile 使用
JUnit 5.14.4。

## 代码示例

在任何受支持的 JDK 上自行运行校验：

```bash
# 将 JAVA_HOME 指向 JDK 8、11、17、21 或 25
./mvnw clean test -pl microsphere-bom-example -am

# 只运行单个测试类
./mvnw test -pl microsphere-bom-example -Dtest=ExampleServiceTest
```

可把本模块的结构作为你自己工程的起点：

```xml
<parent>
    <groupId>io.github.microsphere-projects</groupId>
    <artifactId>microsphere-bom</artifactId>
    <version>${revision}</version> <!-- reactor 之外请替换为 0.3.16 -->
</parent>

<artifactId>microsphere-bom-example</artifactId>
<packaging>jar</packaging>

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

## 版本信息与兼容性

- 自项目早期版本即存在，与主版本线一致（`0.3.x`）。
- 由于它在 CI 中于 JDK 8 至 25 全部编译并通过测试，**本模块中可行的用法在你的项目里于整个支持范围内同样可行**。

## 相关文档

- 开发者源码分析：[../../developer-guide/zh/build-system.md](../../developer-guide/zh/build-system.md)
- 测试 BOM 详情：[testing-bom.md](testing-bom.md)
