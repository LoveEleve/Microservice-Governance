# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Full build (compilation + tests)
./mvnw clean test

# Build without tests
./mvnw clean verify -DskipTests

# Run with a specific JDK (uses toolchains from parent POM)
./mvnw clean test -Djava.version=17

# Single module build
./mvnw clean test -pl microsphere-testing-bom

# Quick local build with a fixed revision
./mvnw clean test -Drevision=0.0.1-SNAPSHOT
```

CI builds with `--batch-mode --update-snapshots` across Java 8, 11, 17, 21, 25 on Temurin (see
`.github/workflows/maven-build.yml`).

## Project Structure

Multi-module Maven POM project that manages dependency versions for the Microsphere ecosystem. All modules are `pom`
-packaged except `microsphere-bom-example` (`jar`).

**Sub-modules:**

| Module                    | Domain             | Key Libraries                                                                 |
|---------------------------|--------------------|-------------------------------------------------------------------------------|
| `microsphere-logging-bom` | Logging frameworks | SLF4J 2.0.18, Logback 1.5.32, Log4j 2.26.0, commons-logging 1.3.6             |
| `microsphere-testing-bom` | Testing frameworks | JUnit 5.14.4/4.13.2, Mockito 5.23.0, Hamcrest 3.0, JMH 1.37, JSONassert 1.5.3 |
| `microsphere-javadb-bom`  | Embedded databases | SQLite JDBC 3.53.1.0, H2 2.4.240                                              |
| `microsphere-javaee-bom`  | Java EE APIs       | Servlet 4.0.1, JAX-RS 2.1.1, CDI 2.0, JPA 2.2, Bean Validation 2.0            |
| `microsphere-tomcat-bom`  | Apache Tomcat      | Tomcat 11.0.22 (embed + full)                                                 |
| `microsphere-all-bom`     | Aggregator         | Imports all above BOMs                                                        |
| `microsphere-bom-example` | Validation         | Consumes all BOM dependencies for integrity verification (no source code)     |

## Version Management

- Single `revision` property in root `pom.xml` (uses `${revision}` placeholder everywhere — the ci-friendly Maven
  `${revision}` approach)
- Current: `0.2.4-SNAPSHOT`
- Release workflow (`.github/workflows/maven-publish.yml`) tags, publishes to Maven Central, auto-bumps to next
  `-SNAPSHOT`, and merges `release` → `main`

## JDK-Specific Profiles

Several BOMs use Maven profiles to select compatible library versions per JDK:

- **logging-bom**: `java8-10` → Logback 1.3.16 (Logback 1.5.x requires Java 11+)
- **testing-bom**: `java8-16` → JUnit Jupiter 5.14.4 / Mockito 4.11.0
- **javadb-bom**: `java8` → H2 1.4.200
- **tomcat-bom**: `java8` → Tomcat 9.0.117; `java11+` → Tomcat 10.1.54; `java17+` → Tomcat 11.0.22; also explicit
  `tomcat9`/`tomcat10` profiles

## Naming Convention

Properties use `.<version>` suffix (e.g. `slf4j.version`). GroupId follows Java reverse-domain convention.

## Adding a New Dependency

1. Add the version property (with `.version` suffix) in the appropriate sub-module's `<properties>` section
2. Add the `<dependency>` entry in that module's `<dependencyManagement>` section
3. If the dependency has different versions per JDK, add a Maven profile with the override
4. If it should be available to all consumers, also add it to `microsphere-all-bom`
