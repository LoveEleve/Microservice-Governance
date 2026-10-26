# Microsphere BOM — User Guide

> 🇨🇳 [中文版用户文档](zh/README.md)

This User Guide documents every Maven module of the `microsphere-bom` project as published from the `release`
branch (version line `0.3.x`, latest published version **0.3.16**). It is written for **consumers** of the BOM
artifacts: how to import each BOM, which dependency versions are managed, what the effective "API" of a BOM is,
code examples, and JDK compatibility ranges.

## What is a BOM?

A Bill of Materials (BOM) is a `pom`-packaged Maven artifact whose `<dependencyManagement>` section pins versions of
third-party libraries. Importing a BOM lets you declare dependencies **without version numbers**:

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
    <!-- No <version/> needed — resolved from the BOM -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
    </dependency>
</dependencies>
```

## Modules

| Module | Type | Purpose | Guide |
|--------|------|---------|-------|
| `microsphere-all-bom` | BOM (aggregator) | One-stop import of all domain BOMs | [all-bom.md](all-bom.md) |
| `microsphere-logging-bom` | BOM | Logging: SLF4J, Logback, Log4j, Log4j2, Commons Logging | [logging-bom.md](logging-bom.md) |
| `microsphere-testing-bom` | BOM | Testing: JUnit 4/5, Mockito, Hamcrest, JMH, JSONassert | [testing-bom.md](testing-bom.md) |
| `microsphere-javadb-bom` | BOM | Embedded databases: SQLite JDBC, H2 | [javadb-bom.md](javadb-bom.md) |
| `microsphere-javaee-bom` | BOM | Java EE `javax.*` API artifacts | [javaee-bom.md](javaee-bom.md) |
| `microsphere-tomcat-bom` | BOM | Apache Tomcat embed + full libraries | [tomcat-bom.md](tomcat-bom.md) |
| `microsphere-bom-example` | JAR (internal) | Validation project proving BOM integrity (reference for test code examples) | [bom-example.md](bom-example.md) |

## Where to Start

1. **Just need sane versions for common libraries?** → Import [`microsphere-all-bom`](all-bom.md).
2. **Only one technology domain?** → Import the corresponding domain BOM (logging / testing / javadb / javaee / tomcat).
3. **Which version works with my JDK?** → See [Versions & Compatibility](versions.md). It documents the release
   history, the JDK-activated version rules (Maven profiles), and the compatibility matrix for every managed library.

## Key Facts at a Glance (release / 0.3.16)

| Fact | Value |
|------|-------|
| GroupId of all artifacts | `io.github.microsphere-projects` |
| Latest published version | `0.3.16` (branch HEAD: `0.3.17-SNAPSHOT`) |
| Tested JDKs (CI matrix) | 8, 11, 17, 21, 25 (Eclipse Temurin) |
| Parent build POM | `io.github.microsphere-projects:microsphere-build` |
| License | Apache License 2.0 |

## Guides

- Index of all documentation: [docs/README.md](../../README.md)
- Developer Guide (architecture & source analysis): [../developer-guide/README.md](../developer-guide/README.md)
