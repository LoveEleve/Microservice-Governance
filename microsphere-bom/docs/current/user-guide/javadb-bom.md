# microsphere-javadb-bom — User Guide

> Part of the [Microsphere BOM User Guide](README.md) · [中文文档](zh/javadb-bom.md)

## Overview

`microsphere-javadb-bom` manages versions of embedded / file-based Java databases — SQLite JDBC and H2 — which are
typically used for development, testing and lightweight persistence.

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `io.github.microsphere-projects` |
| artifactId | `microsphere-javadb-bom` |
| version | `0.3.16` (release line `0.3.x`) |
| packaging | `pom` (BOM) |

## API Explanation

- Two coordinates are managed; both are ordinary JDBC drivers, consumed as normal `runtime`/`test` dependencies.
- Version properties: `sqlite-jdbc.version`, `h2.version` (override with the first-declaration-wins pattern, as
  described in [logging-bom.md](logging-bom.md)).
- The `java8` profile downgrades H2 for legacy build environments: H2 2.x requires Java 8+, so builds running on
  Java 7 or below fall back to **1.4.200**, the last 1.4.x release.

## Managed Dependencies

| GroupId | ArtifactId | Version Property | Default | JDK Override |
|---------|------------|------------------|---------|--------------|
| `org.xerial` | `sqlite-jdbc` | `sqlite-jdbc.version` | 3.53.4.0 | — |
| `com.h2database` | `h2` | `h2.version` | 2.5.252 | 1.4.200 when JDK `(, 1.8]` |

## Code Examples

Unit tests against an in-memory H2 database — the version-free declaration style is exactly the one validated by
`microsphere-bom-example`'s test suite (`ExampleServiceTest`):

```xml
<dependencies>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

```java
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class H2RepositoryTest {

    @Test
    public void testInMemoryRoundTrip() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE person (id INT PRIMARY KEY, name VARCHAR(64))");
            statement.execute("INSERT INTO person VALUES (1, 'Microsphere')");
            try (ResultSet rs = statement.executeQuery("SELECT name FROM person WHERE id = 1")) {
                rs.next();
                assertEquals("Microsphere", rs.getString(1));
            }
        }
    }
}
```

SQLite (bundled native driver, zero installation):

```xml
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <scope>runtime</scope>
</dependency>
```

```java
try (Connection connection = DriverManager.getConnection("jdbc:sqlite:app.db")) {
    // standard JDBC against a self-contained database file
}
```

## Version Information & Compatibility

- **BOM version**: release line `0.3.x` (latest **0.3.16**).
- **JDK compatibility**:

| Library | Version | Minimum JDK | Notes |
|---------|---------|-------------|-------|
| SQLite JDBC | 3.53.4.0 | Java 8 | xerial build bundles natives for major OS/arch combos |
| H2 | 2.5.252 | Java 8 (2.x line) | When the *build* runs on Java 7 or below (profile activation `(, 1.8]`), **1.4.200** is selected instead — 1.4.x is the last line compatible with such legacy JDKs |

- **Profiles**: `java8` (activated for JDK `(, 1.8]`) → `h2.version=1.4.200`.

> ⚠️ H2 1.4.200 and 2.x differ in SQL dialect details, `MVStore` storage format and URL options. Pin the version
> explicitly if you must interop with existing database files.

## See Also

- Developer analysis: [../../developer-guide/javadb-bom.md](../developer-guide/javadb-bom.md)
- Versions overview: [versions.md](versions.md)
