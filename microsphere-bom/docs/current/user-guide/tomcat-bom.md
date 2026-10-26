# microsphere-tomcat-bom — User Guide

> Part of the [Microsphere BOM User Guide](README.md) · [中文文档](zh/tomcat-bom.md)

## Overview

`microsphere-tomcat-bom` manages versions of the Apache Tomcat artifacts in two flavors:

- **`org.apache.tomcat.embed`** — embeddable jars (`tomcat-embed-core`, `-jasper`, `-el`, `-websocket`) for
  applications that start Tomcat in-process (the Spring Boot style).
- **`org.apache.tomcat`** — the full modular Tomcat distribution artifacts (catalina, coyote, jasper, jdbc,
  tribes, …) for tooling and container-level integrations.

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `io.github.microsphere-projects` |
| artifactId | `microsphere-tomcat-bom` |
| version | `0.3.16` (release line `0.3.x`) |
| packaging | `pom` (BOM) |

## API Explanation

- One property drives almost every artifact: `tomcat.version`. The single exception is the extra
  `tomcat-websocket-client-api` coordinate, managed only in the `java17+` profile.
- **Three auto-activating JDK profiles** shape `tomcat.version` — the latest-declared active profile wins, so the
  effective default on each JDK is:

| Build JDK | Active Profiles (declaration order) | Effective `tomcat.version` |
|-----------|--------------------------------------|----------------------------|
| ≤ 8 | `java8` | **9.0.120** |
| 11–16 | `java11+` | **10.1.57** |
| ≥ 17 | `java11+`, then `java17+` (wins) | **11.0.24** |

Profiles are activated against the JDK of the Maven instance that resolves the build — for an imported BOM that is
**your** build environment. The base `<properties>` value (**11.0.27**) is what remains when no JDK profile
matches. For fully deterministic builds, pin the version explicitly (first-declaration-wins override or
`-Ptomcat9` / `-Ptomcat10`).

- Manual overrides: the non-activated profiles `tomcat9` (pins 9.0.120) and `tomcat10` (pins 10.1.57) can be
  switched on explicitly with `-Ptomcat9` / `-Ptomcat10`.

## Managed Dependencies

All coordinates below share the single effective `tomcat.version` unless noted:

| GroupId | ArtifactIds |
|---------|-------------|
| `org.apache.tomcat.embed` | `tomcat-embed-core`, `tomcat-embed-jasper`, `tomcat-embed-el`, `tomcat-embed-websocket` |
| `org.apache.tomcat` | `tomcat-api`, `tomcat-catalina`, `tomcat-catalina-ant`, `tomcat-catalina-ha`, `tomcat-coyote`, `tomcat-coyote-ffm`, `tomcat-dbcp`, `tomcat-jasper`, `tomcat-jasper-el`, `tomcat-jaspic-api`, `tomcat-jdbc`, `tomcat-jni`, `tomcat-juli`, `tomcat-ssi`, `tomcat-storeconfig`, `tomcat-tribes`, `tomcat-util`, `tomcat-util-scan`, `tomcat-websocket`, `tomcat-websocket-api` |
| `org.apache.tomcat` (only under `java17+` profile) | `tomcat-websocket-client-api` |

## Code Examples

Embedding Tomcat 11 in a `main()` (idiomatic embedded bootstrap):

```xml
<dependencies>
    <dependency>
        <groupId>org.apache.tomcat.embed</groupId>
        <artifactId>tomcat-embed-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.apache.tomcat.embed</groupId>
        <artifactId>tomcat-embed-jasper</artifactId>
    </dependency>
</dependencies>
```

```java
import org.apache.catalina.startup.Tomcat;

import java.io.File;

public class EmbeddedTomcatLauncher {

    public static void main(String[] args) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(8080);
        tomcat.getConnector(); // trigger connector creation

        String docBase = new File(".").getAbsolutePath();
        tomcat.addContext("", docBase);

        // A servlet following the same null/empty contract style as
        // ExampleService.greet(name) in the test-case project
        Tomcat.addServlet("", "status", (req, resp) -> {
            String who = req.getParameter("name");
            if (who == null || who.isEmpty()) {
                resp.sendError(400, "name must not be null or empty");
                return;
            }
            resp.setContentType("text/plain");
            resp.getWriter().write("Hello, " + who + "!");
        });

        tomcat.start();
        tomcat.getServer().await();
    }
}
```

> With Tomcat 10.1+/11 the servlet API package is `jakarta.servlet.*`, not `javax.servlet.*` — pair this BOM with
> [microsphere-javaee-bom](javaee-bom.md) **only** when staying on Tomcat 9 (`-Ptomcat9`).

## Version Information & Compatibility

- **BOM version**: release line `0.3.x` (latest **0.3.16**).
- **Tomcat line compatibility**:

| Tomcat Line | Managed Version Here | Minimum JDK | Servlet API Namespace | Jakarta EE |
|-------------|----------------------|-------------|------------------------|------------|
| 9.0.x | 9.0.120 | Java 8 | `javax.servlet.*` | Java EE 8 |
| 10.1.x | 10.1.57 | Java 11 | `jakarta.servlet.*` | Jakarta EE 9/10 |
| 11.0.x | 11.0.24 / 11.0.27 | Java 17 | `jakarta.servlet.*` | Jakarta EE 10/11 |

- **Profiles**: `java8` (`(,1.8]` → 9.0.120), `java11+` (`[11,)` → 10.1.57), `java17+` (`[17,)` → 11.0.24 +
  websocket-client-api), manual `tomcat9` (9.0.120), `tomcat10` (10.1.57).

## See Also

- Developer analysis (profile precedence walkthrough): [../../developer-guide/tomcat-bom.md](../developer-guide/tomcat-bom.md)
- Versions overview: [versions.md](versions.md)
