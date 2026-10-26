# microsphere-javadb-bom — 用户文档

> 本文属于 [Microsphere BOM 用户文档](README.md) · 英文版见 [../javadb-bom.md](../javadb-bom.md)

## 概述

`microsphere-javadb-bom` 管理嵌入式/文件型 Java 数据库的版本 —— SQLite JDBC 与 H2，通常用于开发、测试与轻量级
持久化场景。

## Maven 坐标

| 字段 | 值 |
|------|-----|
| groupId | `io.github.microsphere-projects` |
| artifactId | `microsphere-javadb-bom` |
| version | `0.3.16`（release 版本线 `0.3.x`） |
| packaging | `pom`（BOM） |

## API 解释

- 仅托管两个坐标；均为标准 JDBC 驱动，按普通 `runtime`/`test` 依赖消费。
- 版本属性：`sqlite-jdbc.version`、`h2.version`（覆盖方式同 [logging-bom.md](logging-bom.md) 中的
  "先声明者优先"模式）。
- `java8` profile 为老旧构建环境降级 H2：H2 2.x 要求 Java 8+，因此构建 JDK 为 Java 7 及以下时回退到
  **1.4.200**（1.4.x 的最后一个版本）。

## 托管依赖清单

| GroupId | ArtifactId | 版本属性 | 默认 | JDK 覆盖 |
|---------|------------|----------|------|----------|
| `org.xerial` | `sqlite-jdbc` | `sqlite-jdbc.version` | 3.53.4.0 | — |
| `com.h2database` | `h2` | `h2.version` | 2.5.252 | JDK `(, 1.8]` 时为 1.4.200 |

## 代码示例

基于内存 H2 数据库的单元测试 —— 无版本声明风格与 `microsphere-bom-example` 测试套件
（`ExampleServiceTest`）验证的模式一致：

```xml
<dependencies>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

```java
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class H2RepositoryTest {

    @Test
    public void testInMemoryRoundTrip() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE person (id INT PRIMARY KEY, name VARCHAR(64))");
            statement.execute("INSERT INTO person VALUES (1, 'Microsphere')");
            try (ResultSet rs = statement.executeQuery("SELECT name FROM person WHERE id = 1")) {
                rs.next();
                assertEquals("Microsphere", rs.getString(1));
            }
        }
    }
}
```

SQLite（内置原生库驱动，零安装）：

```xml
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <scope>runtime</scope>
</dependency>
```

```java
try (Connection connection = DriverManager.getConnection("jdbc:sqlite:app.db")) {
    // 以标准 JDBC 访问自包含的数据库文件
}
```

## 版本信息与兼容性

- **BOM 版本**：版本线 `0.3.x`（最新 **0.3.16**）。
- **JDK 兼容性**：

| 库 | 版本 | 最低 JDK | 说明 |
|----|------|----------|------|
| SQLite JDBC | 3.53.4.0 | Java 8 | xerial 发行版为主流 OS/架构打包原生库 |
| H2 | 2.5.252 | Java 8（2.x 线） | 构建 JDK 为 Java 7 及以下时（profile 激活条件 `(, 1.8]`）改用 **1.4.200** —— 1.4.x 是兼容这类老旧 JDK 的最后版本线 |

- **Profile**：`java8`（JDK `(, 1.8]` 激活）→ `h2.version=1.4.200`。

> ⚠️ H2 1.4.200 与 2.x 在 SQL 方言细节、`MVStore` 存储格式与 URL 参数上存在差异。若需与既有数据库文件互操作，
> 请显式固定版本。

## 相关文档

- 开发者源码分析：[../../developer-guide/zh/javadb-bom.md](../../developer-guide/zh/javadb-bom.md)
- 版本总览：[versions.md](versions.md)
