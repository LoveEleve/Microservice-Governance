# microsphere-javadb-bom — Source Analysis

> Part of the [Microsphere BOM Developer Guide](README.md) · [中文文档](zh/javadb-bom.md)

Module source: [`microsphere-javadb-bom/pom.xml`](../../../microsphere-javadb-bom/pom.xml)

## 1. POM Skeleton

The smallest domain BOM — two properties, two managed entries, one profile:

```
<properties>   sqlite-jdbc.version=3.53.4.0 · h2.version=2.5.252
<dependencyManagement>  org.xerial:sqlite-jdbc · com.h2database:h2
<profiles>     java8 → h2.version=1.4.200
```

## 2. Why Only Two Artifacts

Both are **self-contained JDBC drivers** (SQLite bundles platform natives; H2 is pure Java) — there are no
sibling artifacts to keep in lock-step, so no family grouping is needed. This module demonstrates that the domain
boundary is drawn by *usage context* ("embedded database for dev/test"), not by artifact count.

## 3. Profile: `java8` — the `(,1.8]` Range

```xml
<activation><jdk>(,1.8]</jdk></activation>
```

- The open lower bound `(,` means "any version ≤ 1.8" — unlike the other profiles this one also covers Java 5–7
  build environments.
- H2 2.x requires Java 8+; 1.4.200 is pinned as the last 1.4.x release (note: **not** the last 1.4 line — 1.4.200
  *is* its final patch).
- Known-good asymmetry: `sqlite-jdbc` needs no fallback (3.x line is Java 8 compatible, and older xerial builds
  supported Java 6/7 as well).

## 4. Maintenance Notes

- SQLite JDBC versions follow `YYYY.M.m.patch`-style 4-segment numbers from xerial — Dependabot handles them as
  plain increments; treat the trailing `.0` as part of the version, never strip it.
- Upgrading H2 across the 1.4 → 2.x boundary is a **breaking change for consumers** of existing `.mv.db` files;
  the profile mechanism intentionally shields Java 8-era builds from that transition.

## See Also

- User contract: [../user-guide/javadb-bom.md](../user-guide/javadb-bom.md)
- [architecture.md](architecture.md) §6 for the profile-override pattern
