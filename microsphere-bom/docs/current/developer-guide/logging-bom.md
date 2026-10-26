# microsphere-logging-bom — Source Analysis

> Part of the [Microsphere BOM Developer Guide](README.md) · [中文文档](zh/logging-bom.md)

Module source: [`microsphere-logging-bom/pom.xml`](../../../microsphere-logging-bom/pom.xml)

## 1. POM Skeleton

```
<parent>       microsphere-bom:${revision}          ← inheritance only, no managed deps flow down
<properties>   5 x *.version                        ← the single authoring points
<dependencyManagement>  8 entries, all <version> are ${...} indirections
<profiles>     java8-10                             ← property override, not dependency override
```

## 2. Properties Section

```xml
<properties>
    <commons-logging.version>1.4.0</commons-logging.version>
    <slf4j.version>2.0.20</slf4j.version>
    <logback.version>1.6.5</logback.version>   <!-- three artifacts share this knob -->
    <log4j.version>1.2.17</log4j.version>      <!-- Log4j 1.x, kept for legacy -->
    <log4j2.version>2.26.1</log4j2.version>    <!-- two artifacts share this knob -->
</properties>
```

Conventions enforced here:

- Every property ends in `.version`; the prefix is the artifact (or family) name.
- **One property per version *decision***, not per artifact: `logback.version` drives `logback-classic`,
  `logback-core` and `logback-access` because they are released as one train and must never be mixed.

## 3. dependencyManagement Analysis

| Entry | Kind | Rationale |
|-------|------|-----------|
| `commons-logging:commons-logging` | plain | bridges/legacy APIs still pull it |
| `org.slf4j:slf4j-api` | plain | the facade; **only** the API is managed — adapters (`slf4j-log4j12` etc.) are intentionally absent because SLF4J 2.x providers are Logback/Log4j2 themselves |
| `ch.qos.logback:logback-{classic,core,access}` | family | classic depends on core at the exact same version; managing all three prevents skew |
| `log4j:log4j` | plain | 1.2.17 final; EOL artifact kept so legacy transitive requests land on a known version |
| `org.apache.logging.log4j:log4j-{api,core}` | family | same-train rule as Logback |

No `<type>pom</type><scope>import</scope>` entries — this BOM authors raw versions directly (unlike
[testing-bom](testing-bom.md), which nests upstream BOMs).

## 4. Profile: `java8-10`

```xml
<profile>
    <id>java8-10</id>
    <activation><jdk>[1.8,11)</jdk></activation>
    <properties>
        <logback.version>1.3.16</logback.version>
    </properties>
</profile>
```

Design points:

- **Property-override pattern**: the profile changes a *property*, not the managed dependency entries. The
  `dependencyManagement` block stays version-agnostic — the pattern to reuse for every new JDK bifurcation.
- Range `[1.8,11)` covers exactly the JDKs where Logback 1.5/1.6 (Java 11 bytecode) cannot load. 1.3.16 is the
  terminal release of Logback's Java-8 line.
- Edge semantics: JDK `< 1.8` does **not** activate the range (base 1.6.5 is kept) — deliberate, since the project
  floor is Java 8 (see [versions.md](../user-guide/versions.md)).

```mermaid
flowchart TD
    A[resolve logback.version] --> B{JDK in [1.8,11)?}
    B -- yes --> C[profile java8-10 active<br/>logback.version=1.3.16]
    B -- no --> D[base logback.version=1.6.5]
    C & D --> E[interpolate into logback-classic/core/access]
```

## 5. Evolution & Maintenance

- Dependency bumps arrive via Dependabot PRs against `main` → merged → synced to `release` (see
  [ci-cd.md](ci-cd.md)); the version properties are the only diff lines such PRs should touch.
- When Logback starts requiring a newer baseline than the current default, add another profile (e.g. `java11-16`)
  rather than editing the base value — the base must stay the *latest* JDK's answer.

## See Also

- Consumer contract: [../user-guide/logging-bom.md](../user-guide/logging-bom.md)
- Pattern catalog: [architecture.md](architecture.md#6-design-patterns)
