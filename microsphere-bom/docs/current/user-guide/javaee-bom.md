# microsphere-javaee-bom — User Guide

> Part of the [Microsphere BOM User Guide](README.md) · [中文文档](zh/javaee-bom.md)

## Overview

`microsphere-javaee-bom` manages the version set of the **Java EE 8 `javax.*` standard API artifacts** (Web
Profile, Full Platform and Optional APIs, plus JSR 305). It is the right BOM for libraries that compile against
`javax.*` specifications and must run on Java EE containers (Tomcat 9, WildFly, GlassFish…) or on Jakarta EE
containers through a namespace migration tool.

## Maven Coordinates

| Field | Value |
|-------|-------|
| groupId | `io.github.microsphere-projects` |
| artifactId | `microsphere-javaee-bom` |
| version | `0.3.16` (release line `0.3.x`) |
| packaging | `pom` (BOM) |

## API Explanation

- All artifacts are **API-only JARs** (specification classes, no implementation). You normally consume them with
  `<scope>provided</scope>` — the container supplies the implementation at runtime.
- Properties are named after the artifact with a `.version` suffix (`javax.servlet-api.version`,
  `javax.persistence-api.version`, …).
- No JDK profiles: all artifacts target Java 6/7/8 bytecode and are safe on every JDK supported by this project
  (8–25).

## Managed Dependencies

### Web Profile APIs

| GroupId | ArtifactId | Version |
|---------|------------|---------|
| `javax.servlet` | `javax.servlet-api` | 4.0.1 |
| `javax.servlet.jsp` | `javax.servlet.jsp-api` | 2.3.3 |
| `javax.el` | `javax.el-api` | 3.0.0 |
| `javax.servlet.jsp.jstl` | `javax.servlet.jsp.jstl-api` | 1.2.2 |
| `javax.faces` | `jsf-api` | 2.1 |
| `javax.ws.rs` | `javax.ws.rs-api` | 2.1.1 |
| `javax.xml.ws` | `jaxws-api` | 2.3.1 |
| `javax.websocket` | `javax.websocket-api` | 1.1 |
| `javax.json` | `javax.json-api` | 1.1.4 |
| `javax.json.bind` | `javax.json.bind-api` | 1.0 |
| `javax.annotation` | `javax.annotation-api` | 1.3.2 |
| `javax.ejb` | `javax.ejb-api` | 3.2.2 |
| `javax.transaction` | `javax.transaction-api` | 1.3 |
| `javax.persistence` | `javax.persistence-api` | 2.2 |
| `javax.validation` | `validation-api` | 2.0.1.Final |
| `javax.interceptor` | `javax.interceptor-api` | 1.2.2 |
| `javax.enterprise` | `cdi-api` | 2.0.SP1 |
| `javax.inject` | `javax.inject` | 1 |
| `javax.security.auth.message` | `javax.security.auth.message-api` | 1.1.1 |
| `javax.security.enterprise` | `javax.security.enterprise-api` | 1.0 |

### Full Platform APIs

| GroupId | ArtifactId | Version |
|---------|------------|---------|
| `javax.jms` | `javax.jms-api` | 2.0.1 |
| `javax.mail` | `javax.mail-api` | 1.6.2 |
| `com.sun.mail` | `javax.mail` | 1.6.2 (shares `javax.mail-api.version`) |
| `javax.resource` | `javax.resource-api` | 1.7.1 |
| `javax.management.j2ee` | `javax.management.j2ee-api` | 1.1.2 |
| `javax.enterprise.concurrent` | `javax.enterprise.concurrent-api` | 1.1 |
| `javax.batch` | `javax.batch-api` | 1.0.1 |

### Optional APIs & Others

| GroupId | ArtifactId | Version |
|---------|------------|---------|
| `javax.enterprise.deploy` | `javax.enterprise.deploy-api` | 1.7 |
| `javax.xml.registry` | `javax.xml.registry-api` | 1.0.8 |
| `javax.xml.rpc` | `javax.xml.rpc-api` | 1.1.2 |
| `com.google.code.findbugs` | `jsr305` | 3.0.2 |

## Code Examples

A servlet compiled against the managed Servlet API (version-free, `provided`):

```xml
<dependency>
    <groupId>javax.servlet</groupId>
    <artifactId>javax.servlet-api</artifactId>
    <scope>provided</scope>
</dependency>
```

```java
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class StatusServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws java.io.IOException {
        resp.setContentType("text/plain");
        resp.getWriter().println("OK");
    }
}
```

JPA + Bean Validation together (versions resolved from the BOM):

```xml
<dependency>
    <groupId>javax.persistence</groupId>
    <artifactId>javax.persistence-api</artifactId>
</dependency>
<dependency>
    <groupId>javax.validation</groupId>
    <artifactId>validation-api</artifactId>
</dependency>
```

The null/empty parameter contract used by `ExampleService.greet(...)` in the test-case project maps naturally to
Bean Validation here:

```java
import javax.validation.constraints.NotNull;

public String greet(@NotNull String name) { /* IllegalArgumentException-style contract becomes @NotNull */ ... }
```

## Version Information & Compatibility

- **BOM version**: release line `0.3.x` (latest **0.3.16**).
- **Specification level**: Java EE 8 (the last release under the `javax.*` namespace and the Oracle-led JCP).
- **JDK compatibility**: Java 8 through 25 (API jars are Java 6/7/8 bytecode; the JDK itself no longer bundles
  most of these modules since Java 11 — that is exactly why the explicit `provided` dependencies matter).
- **Jakarta EE migration**: Jakarta EE 9+ renamed every package to `jakarta.*`. This BOM deliberately stays on
  `javax.*`; for Jakarta EE 9/10/11 use equivalent `jakarta.*` BOMs or the Eclipse Transformer.

| If your runtime is… | Use |
|----------------------|-----|
| Tomcat 9 / WildFly 27- / GlassFish 5 | this BOM (`javax.*`) |
| Tomcat 10.1+ / Jakarta EE 9–11 containers | `jakarta.*` artifacts (not in this BOM) |

## See Also

- Developer analysis: [../../developer-guide/javaee-bom.md](../developer-guide/javaee-bom.md)
- Tomcat pairing: [tomcat-bom.md](tomcat-bom.md)
- Versions overview: [versions.md](versions.md)
