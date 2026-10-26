# Microsphere BOM — User Guide

## Overview

Microsphere BOM is a set of Bill of Materials (BOM) POMs that centralize dependency version management for the
Microsphere ecosystem. Instead of specifying versions for every library in your project, you import the relevant BOM
and declare dependencies without versions. This ensures all projects use compatible, tested versions.

## Choosing a BOM

The project provides six BOM modules targeting different technology domains:

| BOM                       | Purpose                                       | Import Scope                              |
|---------------------------|-----------------------------------------------|-------------------------------------------|
| `microsphere-all-bom`     | **One-stop import** — includes all BOMs below | Use when you need everything              |
| `microsphere-logging-bom` | Logging frameworks (SLF4J, Logback, Log4j)    | Use in any application                    |
| `microsphere-testing-bom` | Testing (JUnit, Mockito, Hamcrest, JMH)       | Use in projects with tests                |
| `microsphere-javadb-bom`  | Embedded databases (SQLite, H2)               | Use when you need a dev/test database     |
| `microsphere-javaee-bom`  | Java EE APIs (Servlet, JAX-RS, CDI, JPA)      | Use in Java EE / Jakarta EE projects      |
| `microsphere-tomcat-bom`  | Apache Tomcat (embed + full)                  | Use when embedding or depending on Tomcat |

For most projects, importing `microsphere-all-bom` is the simplest starting point.

## Getting Started

### Maven

Add the BOM to your `pom.xml` `<dependencyManagement>` section:

```xml

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.microsphere-projects</groupId>
            <artifactId>microsphere-all-bom</artifactId>
            <version>0.2.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Once imported, declare dependencies without versions:

```xml

<dependencies>
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
    </dependency>
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Gradle

```kotlin
dependencies {
    implementation(platform("io.github.microsphere-projects:microsphere-all-bom:0.2.3"))
    implementation("org.slf4j:slf4j-api")
    implementation("ch.qos.logback:logback-classic")
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
```

> Gradle requires you to declare the `junit-bom` separately for JUnit Jupiter because it uses its own BOM.

### Importing Individual BOMs

If you prefer finer granularity, import only the BOMs you need:

```xml

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.microsphere-projects</groupId>
            <artifactId>microsphere-logging-bom</artifactId>
            <version>0.2.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>io.github.microsphere-projects</groupId>
            <artifactId>microsphere-testing-bom</artifactId>
            <version>0.2.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Managed Dependencies by Module

### microsphere-logging-bom

| Library         | GroupId                    | Version                                |
|-----------------|----------------------------|----------------------------------------|
| SLF4J API       | `org.slf4j`                | 2.0.18                                 |
| Logback Classic | `ch.qos.logback`           | 1.5.32 (Java 11+) / 1.3.16 (Java 8-10) |
| Logback Core    | `ch.qos.logback`           | 1.5.32 / 1.3.16                        |
| Logback Access  | `ch.qos.logback`           | 1.5.32 / 1.3.16                        |
| Log4j 2 API     | `org.apache.logging.log4j` | 2.26.0                                 |
| Log4j 2 Core    | `org.apache.logging.log4j` | 2.26.0                                 |
| Log4j 1         | `log4j`                    | 1.2.17                                 |
| Commons Logging | `commons-logging`          | 1.3.6                                  |

> Logback 1.5.x requires Java 11+. On Java 8-10, the `java8-10` profile activates automatically and selects
> Logback 1.3.16.

### microsphere-testing-bom

| Library                  | GroupId           | Version                                |
|--------------------------|-------------------|----------------------------------------|
| JUnit 4                  | `junit`           | 4.13.2                                 |
| JUnit Jupiter (BOM)      | `org.junit`       | 6.1.0 (Java 17+) / 5.14.4 (Java 8-16)  |
| Mockito (BOM)            | `org.mockito`     | 5.23.0 (Java 17+) / 4.11.0 (Java 8-16) |
| Hamcrest                 | `org.hamcrest`    | 3.0                                    |
| JSONassert               | `org.skyscreamer` | 1.5.3                                  |
| JMH Core                 | `org.openjdk.jmh` | 1.37                                   |
| JMH Annotation Processor | `org.openjdk.jmh` | 1.37                                   |

> On Java 8-16, the `java8-16` profile selects JUnit Jupiter 5.14.4 and Mockito 4.11.0 automatically.
> Note that `junit-bom` is itself a BOM (`<type>pom</type>`), so if you import JUnit Jupiter dependencies,
> Gradle users will need to declare the JUnit BOM separately.

### microsphere-javadb-bom

| Library     | GroupId          | Version                               |
|-------------|------------------|---------------------------------------|
| SQLite JDBC | `org.xerial`     | 3.53.1.0                              |
| H2 Database | `com.h2database` | 2.4.240 (Java 11+) / 1.4.200 (Java 8) |

> H2 2.x requires Java 11+. On Java 8, the `java8` profile selects H2 1.4.200.

### microsphere-javaee-bom

Covers the javax.\* Java EE APIs across Web Profile, Full Platform, and Optional APIs:

**Web Profile**

| Artifact                                                      | Version     |
|---------------------------------------------------------------|-------------|
| `javax.servlet:javax.servlet-api`                             | 4.0.1       |
| `javax.servlet.jsp:javax.servlet.jsp-api`                     | 2.3.3       |
| `javax.el:javax.el-api`                                       | 3.0.0       |
| `javax.servlet.jsp.jstl:javax.servlet.jsp.jstl-api`           | 1.2.2       |
| `javax.faces:jsf-api`                                         | 2.1         |
| `javax.ws.rs:javax.ws.rs-api`                                 | 2.1.1       |
| `javax.xml.ws:jaxws-api`                                      | 2.3.1       |
| `javax.websocket:javax.websocket-api`                         | 1.1         |
| `javax.json:javax.json-api`                                   | 1.1.4       |
| `javax.json.bind:javax.json.bind-api`                         | 1.0         |
| `javax.annotation:javax.annotation-api`                       | 1.3.2       |
| `javax.ejb:javax.ejb-api`                                     | 3.2.2       |
| `javax.transaction:javax.transaction-api`                     | 1.3         |
| `javax.persistence:javax.persistence-api`                     | 2.2         |
| `javax.validation:validation-api`                             | 2.0.1.Final |
| `javax.interceptor:javax.interceptor-api`                     | 1.2.2       |
| `javax.enterprise:cdi-api`                                    | 2.0.SP1     |
| `javax.inject:javax.inject`                                   | 1           |
| `javax.security.auth.message:javax.security.auth.message-api` | 1.1.1       |
| `javax.security.enterprise:javax.security.enterprise-api`     | 1.0         |

**Full Platform**

| Artifact                                                      | Version |
|---------------------------------------------------------------|---------|
| `javax.jms:javax.jms-api`                                     | 2.0.1   |
| `javax.mail:javax.mail-api`                                   | 1.6.2   |
| `com.sun.mail:javax.mail`                                     | 1.6.2   |
| `javax.resource:javax.resource-api`                           | 1.7.1   |
| `javax.management.j2ee:javax.management.j2ee-api`             | 1.1.2   |
| `javax.enterprise.concurrent:javax.enterprise.concurrent-api` | 1.1     |
| `javax.batch:javax.batch-api`                                 | 1.0.1   |

**Optional APIs**

| Artifact                                              | Version |
|-------------------------------------------------------|---------|
| `javax.enterprise.deploy:javax.enterprise.deploy-api` | 1.7     |
| `javax.xml.registry:javax.xml.registry-api`           | 1.0.8   |
| `javax.xml.rpc:javax.xml.rpc-api`                     | 1.1.2   |
| `com.google.code.findbugs:jsr305`                     | 3.0.2   |

> These are the javax.\* (pre-Jakarta EE) versions. If you've migrated to Jakarta EE 9+, you will need the
> Jakarta EE equivalents.

### microsphere-tomcat-bom

| Artifact                        | Version (Java 17+) | Version (Java 11) | Version (Java 8) |
|---------------------------------|--------------------|-------------------|------------------|
| All `org.apache.tomcat.embed:*` | 11.0.22            | 10.1.54           | 9.0.117          |
| All `org.apache.tomcat:*`       | 11.0.22            | 10.1.54           | 9.0.117          |

On Java 17+, an additional dependency is available:

- `org.apache.tomcat:tomcat-websocket-client-api:11.0.22`

You can also activate profiles explicitly by name if the auto-detected JDK mapping doesn't match your needs:

- `-Ptomcat9` — forces Tomcat 9.0.117
- `-Ptomcat10` — forces Tomcat 10.1.54

## JDK Compatibility

Supported JDK versions: 8, 11, 17, 21, 25.

The BOM uses Maven profile activation by `-Djdk.version` to select compatible library versions automatically.
This means the same BOM POM works across all supported JDKs — the correct version is selected at build time.

| JDK Range | Logback | JUnit Jupiter | Mockito | H2      | Tomcat  |
|-----------|---------|---------------|---------|---------|---------|
| 8         | 1.3.16  | 5.14.4        | 4.11.0  | 1.4.200 | 9.0.117 |
| 11-16     | 1.5.32  | 5.14.4        | 4.11.0  | 2.4.240 | 10.1.54 |
| 17+       | 1.5.32  | 6.1.0         | 5.23.0  | 2.4.240 | 11.0.22 |

## Overriding a Version

If you need a different version of a managed dependency, declare it explicitly in your `<dependencyManagement>`:

```xml

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.microsphere-projects</groupId>
            <artifactId>microsphere-all-bom</artifactId>
            <version>0.2.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- Override SLF4J to a newer version -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>2.0.20</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Your override must appear **after** the BOM import in the file — Maven uses the first declaration, so overrides come
last.

## FAQ

**Q: Can I use the BOM with Gradle?**

Yes. Use `implementation(platform(...))`. Note that BOMs that themselves import other BOMs (like `junit-bom`) may
require separate platform declarations in Gradle.

**Q: Which BOM should I import for a Spring Boot project?**

Import `microsphere-all-bom` for logging, database, and testing dependencies. Spring Boot has its own
dependency management for the libraries it uses — use the Microsphere BOM for libraries outside Spring Boot's scope.

**Q: How do I verify that all dependencies resolve correctly?**

The `microsphere-bom-example` module depends on every managed library and serves as a canary. Building it
with `./mvnw clean test -pl microsphere-bom-example` will catch resolution errors.

**Q: What if I need a library that isn't in any BOM?**

Submit an issue or pull request. The project is designed to grow as the Microsphere ecosystem expands.

**Q: How do I use JMH with this BOM?**

Add both `jmh-core` and `jmh-generator-annprocess` as dependencies (the annotation processor is needed for
compilation). Both are managed by the testing BOM.
