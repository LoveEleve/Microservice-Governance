# Microsphere BOM Documentation

This directory contains the complete documentation for the [Microsphere BOM](https://github.com/microsphere-projects/microsphere-bom)
project — a set of Bill of Materials (BOM) POMs that centralize third-party dependency versions for the Microsphere
ecosystem.

> 🇨🇳 中文文档入口请见 [docs/zh/README.md](zh/README.md)。

## Documentation Sets

| Document                | Audience                             | Contents                                                                     |
|-------------------------|--------------------------------------|------------------------------------------------------------------------------|
| [User Guide](current/user-guide/README.md)     | Consumers of the BOM artifacts       | How to import each BOM, managed dependency lists, code examples, version & compatibility information |
| [Developer Guide](current/developer-guide/README.md) | Maintainers and contributors         | Architecture design, POM source analysis, design patterns, build & release flows (with flowcharts) |

## Branch-Scoped Documentation

Documentation is only maintained for the release branches:

| Branch          | Documentation Directory | Status |
|-----------------|-------------------------|--------|
| `release`       | [`current/`](current/README.md) | Latest published release line (0.3.x; latest published version **0.3.16**) |
| `release-1.x`   | `1.x/` | **Not created** — the `release-1.x` branch does not exist in this repository. This directory will be added if that branch is created. |

## Repository Layout

```
docs/
├── README.md                    # this file (English index)
├── zh/
│   └── README.md                # Chinese index
└── current/                     # documentation for the `release` branch
    ├── user-guide/              # English user guide (one file per Maven module)
    │   ├── README.md
    │   ├── logging-bom.md
    │   ├── testing-bom.md
    │   ├── javadb-bom.md
    │   ├── javaee-bom.md
    │   ├── tomcat-bom.md
    │   ├── all-bom.md
    │   ├── bom-example.md
    │   ├── versions.md
    │   └── zh/                  # Chinese user guide (identical filenames)
    └── developer-guide/         # English developer guide
        ├── README.md
        ├── architecture.md
        ├── logging-bom.md
        ├── testing-bom.md
        ├── javadb-bom.md
        ├── javaee-bom.md
        ├── tomcat-bom.md
        ├── build-system.md
        ├── ci-cd.md
        └── zh/                  # Chinese developer guide (identical filenames)
```

## Version Information

- **Managed by this BOM**: every third-party library listed in the module docs of the User Guide.
- **BOM artifact version line**: `0.3.x` (Maven Central groupId `io.github.microsphere-projects`).
- **Java compatibility**: JDK 8, 11, 17, 21, 25 (verified by CI on Temurin). See
  [User Guide — Versions & Compatibility](current/user-guide/versions.md) for the per-library JDK rules.

## License

All documentation is released under the [Apache License 2.0](../LICENSE).
