# Microsphere BOM

> Microsphere Projects for Bill of Materials (BOM)

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/microsphere-projects/microsphere-bom)
[![Maven Build](https://github.com/microsphere-projects/microsphere-bom/actions/workflows/maven-build.yml/badge.svg)](https://github.com/microsphere-projects/microsphere-bom/actions/workflows/maven-build.yml)
[![Codecov](https://codecov.io/gh/microsphere-projects/microsphere-bom/branch/main/graph/badge.svg)](https://app.codecov.io/gh/microsphere-projects/microsphere-bom)
![Maven](https://img.shields.io/maven-central/v/io.github.microsphere-projects/microsphere-bom.svg)
![License](https://img.shields.io/github/license/microsphere-projects/microsphere-bom.svg)

## Introduction

The Microsphere BOM project provides a centralized Bill of Materials (BOM) of third-party libraries for the Microsphere
ecosystem. Its primary purpose is to manage and harmonize dependency versions across multiple projects, ensuring
compatibility and reducing configuration overhead for developers.

By importing this BOM, downstream projects can declare dependencies without specifying version numbers, relying instead
on the curated set of versions maintained within this repository. This project is built upon the [
`microsphere-build`](https://github.com/microsphere-projects/microsphere-build) parent project

### Language Support

Microsphere Build supports the Maven project building on Java TLS versions:

- 8
- 11
- 17
- 21
- 25

## Modules

| **Module**                  | **Purpose**                                                              |
|-----------------------------|--------------------------------------------------------------------------|
| **microsphere-bomdb-bom**   | Manages versions for Java Databases (SQLite, H2, etc.).                  |
| **microsphere-bomee-bom**   | Manages versions for Java EE API (Servlet, JAX-RS, EJB, etc.).           |
| **microsphere-testing-bom** | Manages versions for testing utilities (JUnit, Mockito, Hamcrest, etc.). |
| **microsphere-tomcat-bom**  | Manages versions for Tomcat libraries (Servlet container).               |
| **microsphere-all-bom**     | An aggregator BOM that imports both logging, testing and other BOMs.     |
| **microsphere-bom-example** | A validation module used to verify the BOM integrity.                    |

## Getting Started

The easiest way to get started is by adding the Microsphere BOM (Bill of Materials) to your project's pom.xml:

```xml

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.microsphere-projects</groupId>
            <artifactId>microsphere-all-bom</artifactId>
            <version>${microsphere-bom.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Building from Source

You don't need to build from source unless you want to try out the latest code or contribute to the project.

To build the project, follow these steps:

1. Clone the repository:

```bash
git clone https://github.com/microsphere-projects/microsphere-bom.git
```

2. Build the source:

- Linux/MacOS:

```bash
./mvnw package
```

- Windows:

```powershell
mvnw.cmd package
```

## Contributing

We welcome your contributions! Please read [Code of Conduct](./CODE_OF_CONDUCT.md) before submitting a pull request.

## Reporting Issues

* Before you log a bug, please search the [issues](https://github.com/microsphere-projects/microsphere-bom/issues) to
  see if someone has already reported the problem.
* If the issue doesn't already
  exist, [create a new issue](https://github.com/microsphere-projects/microsphere-bom/issues/new).
* Please provide as much information as possible with the issue report.

## Documentation

* [Documentation Index](./docs/README.md) — 中文文档入口见 [docs/zh/README.md](./docs/zh/README.md)
* [User Guide](./docs/current/user-guide/README.md) — import instructions, managed dependency lists, code examples, versions and JDK compatibility ([中文版](./docs/current/user-guide/zh/README.md))
* [Developer Guide](./docs/current/developer-guide/README.md) — architecture, POM source analysis, design patterns, build & release flows ([中文版](./docs/current/developer-guide/zh/README.md))

## User Guide

[DeepWiki Host](https://deepwiki.com/microsphere-projects/microsphere-bom)

[ZRead Host](https://zread.ai/microsphere-projects/microsphere-bom)

## License

The Microsphere Java is released under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).