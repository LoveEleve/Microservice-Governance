# Versions & Compatibility

> Part of the [Microsphere BOM User Guide](README.md) · [中文文档](zh/versions.md)

This page is the single reference for **which BOM version to use, which library versions it resolves, and which
JDK ranges are supported** on the `release` branch (version line `0.3.x`).

## BOM Release Information

| Item | Value |
|------|-------|
| groupId (all modules) | `io.github.microsphere-projects` |
| Latest published version | **0.3.16** |
| `release` branch HEAD | `0.3.17-SNAPSHOT` (post-release auto-bump) |
| Version line | `0.3.x` (previous lines: `0.1.x`, `0.2.x`) |
| All 7 modules share one version | released from a single reactor; never mix BOM versions across releases |
| Parent build POM | `io.github.microsphere-projects:microsphere-build` |
| License | Apache License 2.0 |

Release history is maintained in the repository root file
[`release-notes.md`](../../../release-notes.md) and on [GitHub Releases](https://github.com/microsphere-projects/microsphere-bom/releases).

## Supported JDK Matrix (verified by CI)

| JDK 8 | JDK 11 | JDK 17 | JDK 21 | JDK 25 |
|-------|--------|--------|--------|--------|
| ✅ Temurin | ✅ Temurin | ✅ Temurin | ✅ Temurin | ✅ Temurin |

CI (`.github/workflows/maven-build.yml`) builds the whole reactor and runs `ExampleServiceTest` on every JDK above.

## Effective Versions per JDK

Because the published BOMs keep their `<profiles>`, the versions below are resolved **in your build** according to
the JDK running Maven:

| Library / Family | JDK ≤ 7 ¹ | JDK 8 | JDK 9–10 | JDK 11–16 | JDK ≥ 17 |
|-------------------|-----------|-------|----------|-----------|----------|
| Logback (logging-bom) | 1.6.5 | **1.3.16** (`java8-10`) | **1.3.16** (`java8-10`) | 1.6.5 | 1.6.5 |
| JUnit Jupiter (testing-bom) | 6.1.3 | **5.14.4** (`java8-16`) | **5.14.4** | **5.14.4** | 6.1.3 |
| Mockito (testing-bom) | 5.24.0 | **4.11.0** (`java8-16`) | **4.11.0** | **4.11.0** | 5.24.0 |
| H2 (javadb-bom) | **1.4.200** (`java8`) | **1.4.200** (`java8`) | 2.5.252 | 2.5.252 | 2.5.252 |
| Tomcat (tomcat-bom) | **9.0.120** (`java8`) | **9.0.120** (`java8`) | **9.0.120** | **10.1.57** (`java11+`) | **11.0.24** (`java17+` wins over `java11+`) |
| Everything else (SLF4J, Log4j/Log4j2, JUnit 4, Hamcrest, JMH, JSONassert, SQLite, javax.* APIs) | uniform across all JDKs | | | | |

¹ Shown for completeness: JDK ≤ 7 activates no JDK profile (activation ranges all start at `1.8`), so Maven keeps
the base property values even though Jupiter 6 / Mockito 5 / Logback 1.6 require newer runtimes. The supported
floor of this BOM line is **JDK 8**.

## Per-Library Compatibility Notes

| Library | Managed Version | Minimum JDK of that version | Notes |
|---------|-----------------|------------------------------|-------|
| SLF4J | 2.0.20 | 8 | 2.x provider (ServiceLoader) mechanism |
| Logback | 1.6.5 / 1.3.16 | 11 / 8 | 1.5+ requires Java 11; 1.3.x is the Java 8 line |
| Log4j2 | 2.26.1 | 8 | |
| Log4j 1.x | 1.2.17 | 5 | EOL, kept for legacy only |
| Commons Logging | 1.4.0 | 8 | |
| JUnit Jupiter | 6.1.3 / 5.14.4 | 17 / 8 | JUnit 6 requires Java 17 |
| Mockito | 5.24.0 / 4.11.0 | 11 / 8 | Mockito 5 dropped Java 8 |
| JUnit 4 | 4.13.2 | 5 | |
| Hamcrest | 3.0 | 8 | |
| JMH | 1.37 | 8 | |
| JSONassert | 1.5.3 | 7 | |
| SQLite JDBC | 3.53.4.0 | 8 | |
| H2 | 2.5.252 / 1.4.200 | 8 / 7 | 1.4.x vs 2.x differ in dialect/storage format |
| javax.* Java EE APIs | see [javaee-bom.md](javaee-bom.md) | 6/7/8 bytecode | Java EE 8 namespace; Jakarta EE 9+ uses `jakarta.*` |
| Apache Tomcat | 11.0.24 / 10.1.57 / 9.0.120 | 17 / 11 / 8 | 9.0 = `javax.servlet`, 10.1+/11 = `jakarta.servlet` |

## Upgrade Guidance

- **0.2.x → 0.3.x**: same module layout; library lines advanced (e.g. Jupiter 5.x → 6.x defaults, Tomcat 11).
  Review the [testing-bom](testing-bom.md) JDK fallbacks if you still build on Java 8.
- To pin a specific library regardless of JDK, use the **first-declaration-wins** override described in
  [logging-bom.md](logging-bom.md).
- Dependabot keeps managed libraries fresh; every bump lands in a patch release (`0.3.n`). See
  [../developer-guide/ci-cd.md](../developer-guide/ci-cd.md) for the release mechanics.

## See Also

- Module-by-module docs: [logging](logging-bom.md) · [testing](testing-bom.md) · [javadb](javadb-bom.md) ·
  [javaee](javaee-bom.md) · [tomcat](tomcat-bom.md) · [all](all-bom.md) · [example](bom-example.md)
- Profile mechanism internals (developer): [../developer-guide/build-system.md](../developer-guide/build-system.md)
