# microsphere-logging-bom — 用户文档

> 本文属于 [Microsphere BOM 用户文档](README.md) · 英文版见 [../logging-bom.md](../logging-bom.md)

## 概述

`microsphere-logging-bom` 集中管理 Java 主流日志库的版本：SLF4J、Logback、Log4j 1.x、Log4j2 与 Apache Commons
Logging。导入该 BOM 后，日志技术栈始终使用一组经过挑选、彼此兼容的版本。

## Maven 坐标

| 字段 | 值 |
|------|-----|
| groupId | `io.github.microsphere-projects` |
| artifactId | `microsphere-logging-bom` |
| version | `0.3.16`（release 版本线 `0.3.x`） |
| packaging | `pom`（BOM） |

## API 解释

BOM 没有运行期 API —— 它的 **API 就是 `dependencyManagement` 契约**：

- 导入 BOM 后，下表中的每个制品都可以**不写 `<version>`** 直接声明。
- 每个托管版本都遵循 `.<version>` 后缀的属性命名约定（如 `slf4j.version`）。
- BOM 通过 `java8-10` Maven profile 提供按 JDK 区分的回退版本（见[兼容性](#版本信息与兼容性)）。

导入示例：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.microsphere-projects</groupId>
            <artifactId>microsphere-logging-bom</artifactId>
            <version>0.3.16</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 托管依赖清单

发布后的 BOM POM 完整保留了 `<properties>` 与 `<profiles>`（父工程使用 flatten-maven-plugin 的
`resolveCiFriendliesOnly` 模式且 `pomElements.properties=keep`）。因此下述 JDK profile 会在**你的项目**导入时
重新求值，"JDK 覆盖"列即你的构建实际解析到的版本。

| GroupId | ArtifactId | 版本属性 | 默认（JDK 11+） | JDK 覆盖 |
|---------|------------|----------|-----------------|----------|
| `commons-logging` | `commons-logging` | `commons-logging.version` | 1.4.0 | — |
| `org.slf4j` | `slf4j-api` | `slf4j.version` | 2.0.20 | — |
| `ch.qos.logback` | `logback-classic` | `logback.version` | 1.6.5 | JDK `[1.8, 11)` 时降为 1.3.16 |
| `ch.qos.logback` | `logback-core` | `logback.version` | 1.6.5 | JDK `[1.8, 11)` 时降为 1.3.16 |
| `ch.qos.logback` | `logback-access` | `logback.version` | 1.6.5 | JDK `[1.8, 11)` 时降为 1.3.16 |
| `log4j` | `log4j` | `log4j.version` | 1.2.17 | — |
| `org.apache.logging.log4j` | `log4j-api` | `log4j2.version` | 2.26.1 | — |
| `org.apache.logging.log4j` | `log4j-core` | `log4j2.version` | 2.26.1 | — |

## 代码示例

典型的 SLF4J + Logback 应用。依赖声明不写版本（与测试用例工程
`microsphere-bom-example/src/test/java/io/github/microsphere/bom/ExampleServiceTest.java` 验证的模式一致——该测试
同样以无版本方式声明 `junit-jupiter` 交由 BOM 解析）：

```xml
<dependencies>
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
    </dependency>
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    public String greet(String name) {
        // 与示例工程 ExampleService.greet(...) 一致的 null/空值契约风格
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name must not be null or empty");
        }
        log.info("Processing order for {}", name);
        return "Hello, " + name + "!";
    }
}
```

在项目内覆盖某个托管版本：Maven 在**BOM 自身**的模型内解析被导入 BOM 的属性，因此在项目里重定义
`logback.version` 并**不会**改变导入值。正确做法是在自己的 `<dependencyManagement>` 中把该依赖声明在 BOM
导入**之前** —— "先声明者优先"：

```xml
<dependencyManagement>
    <dependencies>
        <!-- 先声明的条目优先于随后导入的 BOM -->
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.5.32</version>
        </dependency>
        <!-- 再导入 BOM 托管其余日志依赖 -->
        <dependency>
            <groupId>io.github.microsphere-projects</groupId>
            <artifactId>microsphere-logging-bom</artifactId>
            <version>0.3.16</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 版本信息与兼容性

- **BOM 版本**：自 `0.1.x` 起提供；当前版本线为 `0.3.x`（最新 **0.3.16**）。
- **托管依赖栈的 JDK 兼容性**：

| 库 | 版本 | 最低 JDK | 说明 |
|----|------|----------|------|
| SLF4J 2.0.20 | 2.0.x | Java 8 | SLF4J 2.x 引入 ServiceLoader 提供者机制 |
| Logback 1.6.5 | 1.5+/1.6 | **Java 11** | 因此 `java8-10` profile 降级到 Logback **1.3.16**（1.3.x 为 Java 8 兼容分支） |
| Log4j2 2.26.1 | 2.x | Java 8 | |
| Log4j 1.2.17 | 1.x | Java 5+ | 遗留、已 EOL —— 仅为兼容保留 |
| Commons Logging 1.4.0 | 1.x | Java 8 | |

- **Profile**：`java8-10`（JDK `[1.8, 11)` 自动激活）→ `logback.version=1.3.16`。

## 相关文档

- 开发者源码分析：[../../developer-guide/zh/logging-bom.md](../../developer-guide/zh/logging-bom.md)
- 全模块版本总览：[versions.md](versions.md)
