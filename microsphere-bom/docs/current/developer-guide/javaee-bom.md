# microsphere-javaee-bom — Source Analysis

> Part of the [Microsphere BOM Developer Guide](README.md) · [中文文档](zh/javaee-bom.md)

Module source: [`microsphere-javaee-bom/pom.xml`](../../../microsphere-javaee-bom/pom.xml)

## 1. POM Skeleton

The largest domain BOM (31 managed entries), organized by **Java EE 8 specification profile**, with XML comments
marking the three groups:

```
<properties>  ├ Web Profile APIs (20)   ├ Full Platform APIs (6)   └ Optional APIs (4)
<dependencyManagement>  same grouping in the same order
<profiles>    none — javax.* API jars are JDK-neutral
```

## 2. Grouping Rationale

| Group | Contents | Design note |
|-------|----------|-------------|
| Web Profile | Servlet/JSP/EL/JSTL, JSF, JAX-RS, JAX-WS, WebSocket, JSON-P/JSON-B, Annotation, EJB, Transaction, JPA, Validation, Interceptor, CDI, Inject, Security auth/enterprise | the set a webapp compiles against |
| Full Platform | JMS, Mail (API **and** `com.sun.mail` implementation), Connector (JCA), J2EE Management, Concurrent Utilities, Batch | added only by full-platform users |
| Optional | Deploy, XML Registry, XML-RPC, **JSR 305** | legacy + non-Java-EE extras kept for ecosystem compatibility |

`com.sun.mail:javax.mail` reuses the `javax.mail-api.version` property on purpose: the Jakarta Mail 1.6 API and its
reference implementation ship as one train and must not skew.

## 3. Property Naming — Convention and Deviations

The convention is `javax.<spec>.version`, but watch the deliberate mismatches between **property**, **artifactId**
and **groupId**:

| Property | Artifact managed | Gotcha |
|----------|------------------|--------|
| `javax.messaging-api.version` | `javax.jms:javax.jms-api` | property says "messaging", the Java EE spec name; artifact says "jms" |
| `javax.cdi-api.version` | `javax.enterprise:cdi-api` | groupId/artifactId differ from the property prefix |
| `javax.jsf-api.version` | `javax.faces:jsf-api` | artifact has no `javax.` prefix |
| `javax.validation-api.version` | `javax.validation:validation-api` | same |
| `jsr305.version` | `com.google.code.findbugs:jsr305` | not a `javax.*` coordinate at all |

When renaming, treat property names as **semi-public API**: consumers may reference them; changes are patch-level
noise inside the BOM but breaking in the wild — prefer additions over renames.

## 4. Why There Is No Jakarta Migration Here

Jakarta EE 9+ repackaged every spec (`javax.servlet.*` → `jakarta.servlet.*`), which changes **coordinates and
class names** — that is a different contract, not a version bump. Keeping this BOM frozen at the Java EE 8 set is
what lets `javax.*`-based Microsphere modules stay reproducible. A sibling `jakarta` BOM would be a *new module*,
not an upgrade of this one.

## 5. Maintenance Notes

- The content is near-stable (Java EE 8 is final); routine PRs here are rare — most bumps in this repository
  target logging/testing/javadb/tomcat.
- Versions like `2.0.1.Final` and `2.0.SP1` follow JCP release naming; keep them verbatim.
- If the Microsphere ecosystem ever needs Jakarta EE, do **not** replace entries in place: add a new domain BOM and
  decide whether `microsphere-all-bom` should import both (it currently imports this one).

## See Also

- User contract: [../user-guide/javaee-bom.md](../user-guide/javaee-bom.md)
- [tomcat-bom.md](tomcat-bom.md) — where the `javax`→`jakarta` line actually matters
