# microsphere-testing-bom — User Guide

> Part of the [Microsphere BOM User Guide](README.md) · [中文文档](zh/testing-bom.md)

## Overview

`microsphere-testing-bom` centralizes versions of testing and benchmarking libraries: JUnit 4, JUnit 5 / Jupiter
(via the official `junit-bom`), Mockito (via `mockito-bom`), Hamcrest, JMH and JSONassert. This is the BOM used and
verified by the repository's own test suite (`ExampleServiceTest`).

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `io.github.microsphere-projects` |
| artifactId | `microsphere-testing-bom` |
| version | `0.3.16` (release line `0.3.x`) |
| packaging | `pom` (BOM) |

## API Explanation

- The BOM **nests two upstream BOMs** (`org.junit:junit-bom` and `org.mockito:mockito-bom`, both imported with
  `<type>pom</type><scope>import</scope>`). Consequence: every artifact of the JUnit Jupiter and Mockito
  *families* is version-managed, not just the few coordinates listed explicitly.
- JDK-activated profile `java8-16` downgrades Jupiter and Mockito for JDK `[1.8, 17)` (JUnit 6 and Mockito 5
  require Java 17+).
- Managed versions are exposed as properties (`junit.version`, `junit-jupiter.version`, `mockito.version`, …);
  override them in your project with the **first-declaration-wins** pattern (see
  [logging-bom.md](logging-bom.md#override-a-single-managed-version) — same mechanism).

## Managed Dependencies

| GroupId | ArtifactId | Version Property | Default (JDK 17+) | JDK Override |
|---------|------------|------------------|--------------------|--------------|
| `junit` | `junit` | `junit.version` | 4.13.2 | — |
| `org.junit` | `junit-bom` (import) | `junit-jupiter.version` | **6.1.3** | 5.14.4 on JDK `[1.8, 17)` |
| `org.mockito` | `mockito-bom` (import) | `mockito.version` | **5.24.0** | 4.11.0 on JDK `[1.8, 17)` |
| `org.skyscreamer` | `jsonassert` | `jsonassert.version` | 1.5.3 | — |
| `org.hamcrest` | `hamcrest` | `hamcrest.version` | 3.0 | — |
| `org.openjdk.jmh` | `jmh-core` | `jmh.version` | 1.37 | — |
| `org.openjdk.jmh` | `jmh-generator-annprocess` | `jmh.version` | 1.37 | — |

Because `junit-bom` is imported, coordinates such as `junit-jupiter`, `junit-jupiter-api`,
`junit-jupiter-engine`, `junit-jupiter-params`, and `junit-platform-*` are all resolved automatically. The same
holds for `mockito-core`, `mockito-junit-jupiter`, `mockito-inline`, … via `mockito-bom`.

## Code Examples

This is the exact pattern used by `microsphere-bom-example` — the module's pom imports
`microsphere-all-bom` and declares JUnit Jupiter **without a version**:

```xml
<!-- microsphere-bom-example/pom.xml (excerpt) -->
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

Test code from `ExampleServiceTest.java` (the repository's reference test case):

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

With Mockito (also version-free, resolved through `mockito-bom`):

```xml
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

## Version Information & Compatibility

- **BOM version**: release line `0.3.x` (latest **0.3.16**).
- **JDK compatibility**:

| Library | Default Line | Minimum JDK | JDK 8–16 Fallback | Why |
|---------|--------------|-------------|--------------------|-----|
| JUnit Jupiter | 6.1.3 | Java 17 | 5.14.4 | JUnit 6 requires Java 17+ |
| Mockito | 5.24.0 | Java 11 | 4.11.0 | Mockito 5 dropped Java 8 support; 4.11.0 is the last Java 8 compatible line |
| JUnit 4 | 4.13.2 | Java 5+ | — | unchanged across JDKs |
| Hamcrest 3.0 | 3.0 | Java 8 | — | |
| JMH 1.37 | 1.37 | Java 8 | — | |
| JSONassert 1.5.3 | 1.5.3 | Java 7+ | — | |

- **Profiles**: `java8-16` (auto-activated for JDK `[1.8, 17)`) → `junit-jupiter.version=5.14.4`,
  `mockito.version=4.11.0`.
- **Verified by CI**: the whole reactor (including this BOM and `microsphere-bom-example`) builds and runs tests on
  JDK 8, 11, 17, 21 and 25 (Temurin).

## See Also

- Developer analysis: [../../developer-guide/testing-bom.md](../developer-guide/testing-bom.md)
- Versions overview: [versions.md](versions.md)
