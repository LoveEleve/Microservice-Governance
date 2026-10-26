# microsphere-javaee-bom — 用户文档

> 本文属于 [Microsphere BOM 用户文档](README.md) · 英文版见 [../javaee-bom.md](../javaee-bom.md)

## 概述

`microsphere-javaee-bom` 管理 **Java EE 8 `javax.*` 标准 API 制品**（Web Profile、Full Platform、Optional API
以及 JSR 305）的版本集合。适用于面向 `javax.*` 规范编译、运行于 Java EE 容器（Tomcat 9、WildFly、GlassFish 等），
或经由命名空间迁移工具运行于 Jakarta EE 容器上的类库。

## Maven 坐标

| 字段 | 值 |
|------|-----|
| groupId | `io.github.microsphere-projects` |
| artifactId | `microsphere-javaee-bom` |
| version | `0.3.16`（release 版本线 `0.3.x`） |
| packaging | `pom`（BOM） |

## API 解释

- 所有制品均为**纯 API JAR**（只有规范类，无实现）。通常以 `<scope>provided</scope>` 消费 —— 运行期由容器提供实现。
- 属性按"制品名 + `.version`"命名（`javax.servlet-api.version`、`javax.persistence-api.version` 等）。
- 无 JDK profile：这些制品为 Java 6/7/8 字节码，在本项目支持的全部 JDK（8–25）上均安全。

## 托管依赖清单

### Web Profile API

| GroupId | ArtifactId | 版本 |
|---------|------------|------|
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

### Full Platform API

| GroupId | ArtifactId | 版本 |
|---------|------------|------|
| `javax.jms` | `javax.jms-api` | 2.0.1 |
| `javax.mail` | `javax.mail-api` | 1.6.2 |
| `com.sun.mail` | `javax.mail` | 1.6.2（共用 `javax.mail-api.version`） |
| `javax.resource` | `javax.resource-api` | 1.7.1 |
| `javax.management.j2ee` | `javax.management.j2ee-api` | 1.1.2 |
| `javax.enterprise.concurrent` | `javax.enterprise.concurrent-api` | 1.1 |
| `javax.batch` | `javax.batch-api` | 1.0.1 |

### Optional API 及其他

| GroupId | ArtifactId | 版本 |
|---------|------------|------|
| `javax.enterprise.deploy` | `javax.enterprise.deploy-api` | 1.7 |
| `javax.xml.registry` | `javax.xml.registry-api` | 1.0.8 |
| `javax.xml.rpc` | `javax.xml.rpc-api` | 1.1.2 |
| `com.google.code.findbugs` | `jsr305` | 3.0.2 |

## 代码示例

基于托管 Servlet API 编译的 Servlet（无版本号、`provided`）：

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

JPA 与 Bean Validation 组合使用（版本由 BOM 解析）：

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

测试用例工程中 `ExampleService.greet(...)` 的 null/空值参数契约，在这里自然对应 Bean Validation：

```java
import javax.validation.constraints.NotNull;

public String greet(@NotNull String name) { /* IllegalArgumentException 式契约改为 @NotNull */ ... }
```

## 版本信息与兼容性

- **BOM 版本**：版本线 `0.3.x`（最新 **0.3.16**）。
- **规范级别**：Java EE 8（`javax.*` 命名空间下的最后一个版本）。
- **JDK 兼容性**：Java 8 至 25（API jar 为 Java 6/7/8 字节码；Java 11 起 JDK 本身不再内置大部分这些模块 ——
  这正是显式 `provided` 依赖的意义）。
- **Jakarta EE 迁移**：Jakarta EE 9+ 已把全部包名改为 `jakarta.*`。本 BOM 有意停留在 `javax.*`；
  Jakarta EE 9/10/11 请使用对应的 `jakarta.*` BOM 或 Eclipse Transformer。

| 运行环境 | 选择 |
|-----------|------|
| Tomcat 9 / WildFly 27 以下 / GlassFish 5 | 本 BOM（`javax.*`） |
| Tomcat 10.1+ / Jakarta EE 9–11 容器 | `jakarta.*` 制品（不在本 BOM 中） |

## 相关文档

- 开发者源码分析：[../../developer-guide/zh/javaee-bom.md](../../developer-guide/zh/javaee-bom.md)
- Tomcat 搭配：[tomcat-bom.md](tomcat-bom.md)
- 版本总览：[versions.md](versions.md)
