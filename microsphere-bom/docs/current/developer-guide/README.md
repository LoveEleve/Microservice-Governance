# Microsphere BOM — Developer Guide

> 🇨🇳 [中文版开发者文档](zh/README.md)

This Developer Guide is written for **maintainers and contributors** of the `microsphere-bom` project
(`release` branch, version line `0.3.x`). It explains the architecture design, analyzes the POM "source code"
module by module, documents the Maven design patterns in use, and illustrates complex flows with flowcharts.

> Note: this repository contains almost no Java code — its "source code" is the set of POM files. The only Java
> sources live in `microsphere-bom-example`, used as a build-time integrity check of all BOMs.

## Chapters

| Chapter | Topic |
|---------|-------|
| [architecture.md](architecture.md) | Overall architecture, module dependency graph, reactor build order (flowcharts) |
| [build-system.md](build-system.md) | `${revision}` ci-friendly versioning, flatten plugin, parent `microsphere-build`, JDK profile mechanism (flowcharts) |
| [ci-cd.md](ci-cd.md) | GitHub Actions workflows: build matrix, publish/release flow, branch sync, release-notes generation (flowcharts) |
| [logging-bom.md](logging-bom.md) | Source analysis of `microsphere-logging-bom` |
| [testing-bom.md](testing-bom.md) | Source analysis of `microsphere-testing-bom` |
| [javadb-bom.md](javadb-bom.md) | Source analysis of `microsphere-javadb-bom` |
| [javaee-bom.md](javaee-bom.md) | Source analysis of `microsphere-javaee-bom` |
| [tomcat-bom.md](tomcat-bom.md) | Source analysis of `microsphere-tomcat-bom` |

## What You Will Learn

- **Architecture design** — why the project is split into one aggregator (`microsphere-all-bom`), five domain BOMs,
  one parent POM and one validation module, and how they depend on each other.
- **Source analysis** — a section-by-section walkthrough of every `pom.xml`: properties, `dependencyManagement`,
  BOM imports, and JDK-activated profiles.
- **Design patterns** — Bill of Materials, Dependency Management via `import` scope, Aggregator (reactor) POM,
  property indirection (`<xxx.version>`), profile-based environment activation, ci-friendly `${revision}` +
  flatten-maven-plugin, and the "first declaration wins" precedence rule of Maven dependency management.
- **Flows** — version resolution per JDK, the release pipeline (`release` → tag → Central → auto-bump → merge to
  `main`), and dependabot maintenance, all as Mermaid flowcharts.

## How to Build This Project

```bash
# Full build (all modules, tests in microsphere-bom-example)
./mvnw clean verify

# Single module
./mvnw clean verify -pl microsphere-testing-bom

# Build as if on a specific JDK line (profiles activate from the *build* JDK)
./mvnw clean verify -Djava.version=17
```

## Contributing Checklist

When adding or changing a managed dependency (see [architecture.md](architecture.md#adding-a-managed-dependency)):

1. Add/adjust the `<xxx.version>` property in the owning domain BOM.
2. Add the entry to that module's `<dependencyManagement>`.
3. If the library needs JDK-specific versions, add or update a profile with activation ranges.
4. No change is needed in `microsphere-all-bom` (it imports whole BOMs, not individual artifacts).
5. Run `./mvnw clean verify` on JDK 8/11/17/21/25 (or rely on CI).

## Related Documents

- Documentation index: [docs/README.md](../../../README.md)
- User Guide: [../user-guide/README.md](../user-guide/README.md)
