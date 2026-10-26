# microsphere-bom-example — User Guide

> Part of the [Microsphere BOM User Guide](README.md) · [中文文档](zh/bom-example.md)

## Overview

`microsphere-bom-example` is the repository's **validation module**: an ordinary JAR project that imports
`microsphere-all-bom`, declares dependencies without versions, and compiles + runs real tests against them on every
JDK in the CI matrix (8, 11, 17, 21, 25). It proves the BOM chain resolves end-to-end.

It is **not published to Maven Central** (its `maven-deploy-plugin` is configured with `<skip>true</skip>`) and is
not intended to be consumed as a dependency — treat it as the executable, reference example for how to use the BOMs.

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `io.github.microsphere-projects` |
| artifactId | `microsphere-bom-example` |
| version | `0.3.16` (local reactor only — deploy skipped) |
| packaging | `jar` |

## API Explanation

The module contains one production class and one test class.

### `io.github.microsphere.bom.ExampleService`

| Method | Contract | Throws |
|--------|----------|--------|
| `String greet(String name)` | Returns `"Hello, <name>!"` for any non-empty name | `IllegalArgumentException` — `"name must not be null or empty"` when `name` is `null` or `""` |
| `String getJavaVersion()` | Returns the JVM property `java.specification.version` (e.g. `"17"`) | — (never `null` on any compliant JVM) |

```java
// src/main/java/io/github/microsphere/bom/ExampleService.java (excerpt)
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

### `io.github.microsphere.bom.ExampleServiceTest` — see below

```java
// src/test/java/io/github/microsphere/bom/ExampleServiceTest.java (excerpt)
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

The test class itself exercises the **testing BOM**: `junit-jupiter` is declared version-free and runs on every
JDK of the matrix — JUnit 6.1.3 on 17/21/25, JUnit 5.14.4 on 8/11 (via the `java8-16` profile of
[microsphere-testing-bom](testing-bom.md)).

## Code Examples

Run the validation yourself on any supported JDK:

```bash
# with JAVA_HOME pointing at JDK 8, 11, 17, 21 or 25
./mvnw clean test -pl microsphere-bom-example -am

# single test class
./mvnw test -pl microsphere-bom-example -Dtest=ExampleServiceTest
```

Copy this module's structure as the starting point for your own project:

```xml
<parent>
    <groupId>io.github.microsphere-projects</groupId>
    <artifactId>microsphere-bom</artifactId>
    <version>${revision}</version> <!-- replace with 0.3.16 outside the reactor -->
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

## Version Information & Compatibility

- Exists since the first releases of the project; follows the same version line (`0.3.x`).
- Because it compiles and tests on JDK 8 through 25 in CI, **any usage pattern that works in this module works in
  your project** across the whole supported range.

## See Also

- Developer analysis: [../../developer-guide/build-system.md](../developer-guide/build-system.md)
- Testing BOM details: [testing-bom.md](testing-bom.md)
