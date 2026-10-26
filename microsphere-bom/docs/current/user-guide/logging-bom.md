# microsphere-logging-bom — User Guide

> Part of the [Microsphere BOM User Guide](README.md) · [中文文档](zh/logging-bom.md)

## Overview

`microsphere-logging-bom` centralizes the versions of the most common Java logging libraries: SLF4J, Logback,
Log4j 1.x, Log4j 2 and Apache Commons Logging. Importing it means your logging stack always uses a mutually
compatible, curated set of versions.

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `io.github.microsphere-projects` |
| artifactId | `microsphere-logging-bom` |
| version | `0.3.16` (release line `0.3.x`) |
| packaging | `pom` (BOM) |

## API Explanation

A BOM has no runtime API — its **API is its `dependencyManagement` contract**:

- Every artifact listed below can be declared **without a `<version>`** once the BOM is imported.
- Each managed version is exposed as a Maven property with the `.<version>` suffix convention (e.g.
  `slf4j.version`), so you can *override* a single library in your own `<properties>` without leaving the BOM.
- The BOM activates JDK-specific fallback versions through the `java8-10` Maven profile (see
  [Compatibility](#version-information--compatibility)).

Import snippet:

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

## Managed Dependencies

The published BOM POM keeps its `<properties>` and `<profiles>` intact (the parent build uses
flatten-maven-plugin in `resolveCiFriendliesOnly` mode with `pomElements.properties=keep`). Therefore the JDK
profile below is re-evaluated **in your project** at import time, and the "JDK Override" column is what your
build actually resolves.

| GroupId | ArtifactId | Version Property | Default (JDK 11+) | JDK Override |
|---------|------------|------------------|-------------|--------------|
| `commons-logging` | `commons-logging` | `commons-logging.version` | 1.4.0 | — |
| `org.slf4j` | `slf4j-api` | `slf4j.version` | 2.0.20 | — |
| `ch.qos.logback` | `logback-classic` | `logback.version` | 1.6.5 | 1.3.16 on JDK `[1.8, 11)` |
| `ch.qos.logback` | `logback-core` | `logback.version` | 1.6.5 | 1.3.16 on JDK `[1.8, 11)` |
| `ch.qos.logback` | `logback-access` | `logback.version` | 1.6.5 | 1.3.16 on JDK `[1.8, 11)` |
| `log4j` | `log4j` | `log4j.version` | 1.2.17 | — |
| `org.apache.logging.log4j` | `log4j-api` | `log4j2.version` | 2.26.1 | — |
| `org.apache.logging.log4j` | `log4j-core` | `log4j2.version` | 2.26.1 | — |

## Code Examples

A typical SLF4J + Logback application. Dependencies are declared without versions (the pattern verified by the
test case `microsphere-bom-example/src/test/java/io/github/microsphere/bom/ExampleServiceTest.java`, which likewise
declares `junit-jupiter` version-free and lets the BOM resolve it):

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
        // Same null/empty contract style as ExampleService.greet(...) in the test-case project
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name must not be null or empty");
        }
        log.info("Processing order for {}", name);
        return "Hello, " + name + "!";
    }
}
```

Override a single managed version from your project. Maven resolves the properties of an imported BOM inside the
**BOM's own** model, so redefining `logback.version` in your project does *not* change the imported value. Instead,
declare the dependency **before** the BOM import in your own `<dependencyManagement>` — the first declaration wins:

```xml
<dependencyManagement>
    <dependencies>
        <!-- 1st declaration wins over the imported BOM -->
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.5.32</version>
        </dependency>
        <!-- then import the BOM for everything else -->
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

## Version Information & Compatibility

- **BOM version**: available since the `0.1.x` line; current release line is `0.3.x` (latest **0.3.16**).
- **JDK compatibility of the managed stack**:

| Library | Version | Minimum JDK | Notes |
|---------|---------|-------------|-------|
| SLF4J 2.0.20 | 2.0.x | Java 8 | SLF4J 2.x introduces the ServiceLoader provider mechanism |
| Logback 1.6.5 | 1.5+/1.6 | **Java 11** | Hence the `java8-10` profile downgrades to Logback **1.3.16** (the 1.3.x line is the Java 8 compatible branch) |
| Log4j2 2.26.1 | 2.x | Java 8 | |
| Log4j 1.2.17 | 1.x | Java 5+ | Legacy; EOL — kept for compatibility only |
| Commons Logging 1.4.0 | 1.x | Java 8 | |

- **Profiles**: `java8-10` (auto-activated for `[1.8, 11)`) → `logback.version=1.3.16`.

## See Also

- Developer analysis: [../../developer-guide/logging-bom.md](../developer-guide/logging-bom.md)
- Versions overview for all modules: [versions.md](versions.md)
