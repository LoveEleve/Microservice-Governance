# microsphere-testing-bom — Source Analysis

> Part of the [Microsphere BOM Developer Guide](README.md) · [中文文档](zh/testing-bom.md)

Module source: [`microsphere-testing-bom/pom.xml`](../../../microsphere-testing-bom/pom.xml)

## 1. POM Skeleton

```
<parent>       microsphere-bom:${revision}
<properties>   6 x *.version
<dependencyManagement>
    junit:junit                          ← direct pin (declared before the imports; see §3)
    org.junit:junit-bom        [import]  ← nested BOM #1
    org.mockito:mockito-bom    [import]  ← nested BOM #2
    jsonassert / hamcrest / jmh-core / jmh-generator-annprocess  ← direct pins
<profiles>     java8-16
```

## 2. The Nested-BOM Pattern (delegation)

This is the only domain BOM that **delegates** instead of enumerating:

```xml
<dependency>
    <groupId>org.junit</groupId>
    <artifactId>junit-bom</artifactId>
    <version>${junit-jupiter.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

Key mechanics:

- The property `junit-jupiter.version` doubles as **the coordinate of the upstream BOM itself** — managing the
  entire Jupiter + Platform family (jupiter-api/engine/params, aggregate `junit-jupiter`, vintage engine,
  platform-launcher…) with one knob. Hand-listing those ~15 artifacts would drift on every JUnit release.
- Same trick for Mockito via `mockito.version` → `mockito-bom` (core, junit-jupiter extension, inline…).
- **Property resolution happens in *this* POM's model**, which is why the `java8-16` profile can redirect the
  nested import — a profile cannot reach into a transitive BOM's properties, but it *can* choose *which* BOM to
  import:

```mermaid
flowchart TD
    A[resolve junit-jupiter.version] --> B{JDK in [1.8,17)?}
    B -- yes --> C[5.14.4]
    B -- no --> D[6.1.3]
    C --> E[import org.junit:junit-bom:5.14.4]
    D --> F[import org.junit:junit-bom:6.1.3]
    E & F --> G[all jupiter-* / platform-* coordinates<br/>receive that family's versions]
```

## 3. Ordering and Precedence

The `dependencyManagement` list order is: `junit:junit` (direct) → `junit-bom` (import) → `mockito-bom` (import) →
direct pins. Maven's **first-declaration-wins** rule means:

- The explicit `junit:junit 4.13.2` is declared **before** the `junit-bom` import, so it wins under
  first-declaration-wins even if a future upstream BOM starts managing that coordinate.
- Imported BOMs never see each other's coordinates as conflicts (JUnit and Mockito families are disjoint).

## 4. Profile: `java8-16`

```xml
<profile>
    <id>java8-16</id>
    <activation><jdk>[1.8,17)</jdk></activation>
    <properties>
        <junit-jupiter.version>5.14.4</junit-jupiter.version>
        <mockito.version>4.11.0</mockito.version>
    </properties>
</profile>
```

- One profile, two fallbacks — the two libraries have the same practical Java-8/11–16 compatibility cliff
  (JUnit 6 needs 17; Mockito 5 needs 11 and is only *tested* here from 17 up).
- The name `java8-16` documents intent (the JDK band), the range `[1.8,17)` encodes it.
- Hamcrest/JMH/JSONassert/JUnit 4 are JDK-agnostic at these versions — no profile entries for them.

## 5. What This Module Proves Elsewhere

`microsphere-bom-example` depends on `org.junit.jupiter:junit-jupiter` with **no version** — the aggregate artifact
exists *only* because `junit-bom` is imported. The CI runs `ExampleServiceTest` on all five JDKs, so the nested-BOM
+ profile mechanism is executed, not just declared. See [bom-example](../user-guide/bom-example.md) usage and
[build-system.md](build-system.md) for how the reactor validates it.

## See Also

- User-facing contract: [../user-guide/testing-bom.md](../user-guide/testing-bom.md)
- [architecture.md](architecture.md) — nested BOM pattern in the big picture
