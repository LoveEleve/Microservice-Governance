# microsphere-tomcat-bom — 用户文档

> 本文属于 [Microsphere BOM 用户文档](README.md) · 英文版见 [../tomcat-bom.md](../tomcat-bom.md)

## 概述

`microsphere-tomcat-bom` 管理 Apache Tomcat 两类制品的版本：

- **`org.apache.tomcat.embed`** —— 可嵌入 jar（`tomcat-embed-core`、`-jasper`、`-el`、`-websocket`），用于在进程内
  启动 Tomcat 的应用（Spring Boot 风格）。
- **`org.apache.tomcat`** —— 完整模块化 Tomcat 发行版制品（catalina、coyote、jasper、jdbc、tribes 等），用于工具链
  与容器级集成。

## Maven 坐标

| 字段 | 值 |
|------|-----|
| groupId | `io.github.microsphere-projects` |
| artifactId | `microsphere-tomcat-bom` |
| version | `0.3.16`（release 版本线 `0.3.x`） |
| packaging | `pom`（BOM） |

## API 解释

- 几乎所有制品由单一属性 `tomcat.version` 驱动；唯一例外是 `tomcat-websocket-client-api` 坐标，仅在 `java17+`
  profile 中托管。
- **三个自动激活的 JDK profile** 共同决定 `tomcat.version` —— 同名属性以**最后声明的激活 profile** 为准，因此各
  JDK 上的有效默认值为：

| 构建 JDK | 激活的 Profile（按声明顺序） | 有效 `tomcat.version` |
|-----------|-------------------------------|------------------------|
| ≤ 8 | `java8` | **9.0.120** |
| 11–16 | `java11+` | **10.1.57** |
| ≥ 17 | `java11+`，随后 `java17+`（生效） | **11.0.24** |

  profile 的激活依据是**执行解析的 Maven 实例所用 JDK**；对被导入的 BOM 而言就是你的构建环境。基础
  `<properties>` 值（**11.0.27**）是所有 JDK profile 均不匹配时的取值。若需要完全确定的构建，请显式固定版本
  （"先声明者优先"覆盖，或 `-Ptomcat9` / `-Ptomcat10`）。
- 手动覆盖：未被自动激活的 profile `tomcat9`（固定 9.0.120）与 `tomcat10`（固定 10.1.57）可通过
  `-Ptomcat9` / `-Ptomcat10` 显式开启。

## 托管依赖清单

除特别说明外，下列坐标共享同一个有效的 `tomcat.version`：

| GroupId | ArtifactId |
|---------|------------|
| `org.apache.tomcat.embed` | `tomcat-embed-core`、`tomcat-embed-jasper`、`tomcat-embed-el`、`tomcat-embed-websocket` |
| `org.apache.tomcat` | `tomcat-api`、`tomcat-catalina`、`tomcat-catalina-ant`、`tomcat-catalina-ha`、`tomcat-coyote`、`tomcat-coyote-ffm`、`tomcat-dbcp`、`tomcat-jasper`、`tomcat-jasper-el`、`tomcat-jaspic-api`、`tomcat-jdbc`、`tomcat-jni`、`tomcat-juli`、`tomcat-ssi`、`tomcat-storeconfig`、`tomcat-tribes`、`tomcat-util`、`tomcat-util-scan`、`tomcat-websocket`、`tomcat-websocket-api` |
| `org.apache.tomcat`（仅 `java17+` profile 下） | `tomcat-websocket-client-api` |

## 代码示例

在 `main()` 中嵌入 Tomcat 11（标准嵌入式启动写法）：

```xml
<dependencies>
    <dependency>
        <groupId>org.apache.tomcat.embed</groupId>
        <artifactId>tomcat-embed-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.apache.tomcat.embed</groupId>
        <artifactId>tomcat-embed-jasper</artifactId>
    </dependency>
</dependencies>
```

```java
import org.apache.catalina.startup.Tomcat;

import java.io.File;

public class EmbeddedTomcatLauncher {

    public static void main(String[] args) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(8080);
        tomcat.getConnector(); // 触发 Connector 创建

        String docBase = new File(".").getAbsolutePath();
        tomcat.addContext("", docBase);

        // 与测试用例工程 ExampleService.greet(name) 相同的 null/空值契约风格
        Tomcat.addServlet("", "status", (req, resp) -> {
            String who = req.getParameter("name");
            if (who == null || who.isEmpty()) {
                resp.sendError(400, "name must not be null or empty");
                return;
            }
            resp.setContentType("text/plain");
            resp.getWriter().write("Hello, " + who + "!");
        });

        tomcat.start();
        tomcat.getServer().await();
    }
}
```

> Tomcat 10.1+/11 的 Servlet API 包名是 `jakarta.servlet.*` 而非 `javax.servlet.*` —— 只有在停留在 Tomcat 9
>（`-Ptomcat9`）时才应与 [microsphere-javaee-bom](javaee-bom.md) 搭配使用。

## 版本信息与兼容性

- **BOM 版本**：版本线 `0.3.x`（最新 **0.3.16**）。
- **Tomcat 版本线兼容性**：

| Tomcat 版本线 | 此处托管版本 | 最低 JDK | Servlet API 命名空间 | Jakarta EE |
|---------------|--------------|----------|-----------------------|------------|
| 9.0.x | 9.0.120 | Java 8 | `javax.servlet.*` | Java EE 8 |
| 10.1.x | 10.1.57 | Java 11 | `jakarta.servlet.*` | Jakarta EE 9/10 |
| 11.0.x | 11.0.24 / 11.0.27 | Java 17 | `jakarta.servlet.*` | Jakarta EE 10/11 |

- **Profile**：`java8`（`(,1.8]` → 9.0.120）、`java11+`（`[11,)` → 10.1.57）、`java17+`（`[17,)` → 11.0.24 并附加
  websocket-client-api）、手动 `tomcat9`（9.0.120）、`tomcat10`（10.1.57）。

## 相关文档

- 开发者源码分析（profile 优先级推演）：[../../developer-guide/zh/tomcat-bom.md](../../developer-guide/zh/tomcat-bom.md)
- 版本总览：[versions.md](versions.md)
