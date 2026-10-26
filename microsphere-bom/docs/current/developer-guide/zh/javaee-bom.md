# microsphere-javaee-bom — 源码分析

> 本文属于 [Microsphere BOM 开发者文档](README.md) · 英文版见 [../javaee-bom.md](../javaee-bom.md)

模块源码：[`microsphere-javaee-bom/pom.xml`](../../../../microsphere-javaee-bom/pom.xml)

## 1. POM 骨架

最大的领域 BOM（31 条托管），按 **Java EE 8 规范 profile** 组织，XML 注释标出三个分组：

```
<properties>  ├ Web Profile APIs（20）   ├ Full Platform APIs（6）   └ Optional APIs（4）
<dependencyManagement>  同样的分组与顺序
<profiles>    无 —— javax.* API jar 与 JDK 无关
```

## 2. 分组理由

| 分组 | 内容 | 设计说明 |
|------|------|----------|
| Web Profile | Servlet/JSP/EL/JSTL、JSF、JAX-RS、JAX-WS、WebSocket、JSON-P/JSON-B、Annotation、EJB、Transaction、JPA、Validation、Interceptor、CDI、Inject、Security auth/enterprise | Web 应用编译所需的集合 |
| Full Platform | JMS、Mail（API **与** `com.sun.mail` 实现）、Connector（JCA）、J2EE Management、Concurrent Utilities、Batch | 仅全平台用户使用 |
| Optional | Deploy、XML Registry、XML-RPC、**JSR 305** | 遗留条目 + 非 Java EE 的生态补充 |

`com.sun.mail:javax.mail` 有意复用 `javax.mail-api.version` 属性：Jakarta Mail 1.6 的 API 与参考实现同列车发布，
不允许错配。

## 3. 属性命名 —— 约定与偏差

约定是 `javax.<spec>.version`，但注意 **属性名、artifactId、groupId** 三者之间的刻意偏差：

| 属性 | 实际托管制品 | 陷阱 |
|------|--------------|------|
| `javax.messaging-api.version` | `javax.jms:javax.jms-api` | 属性用规范名 "messaging"，制品名是 "jms" |
| `javax.cdi-api.version` | `javax.enterprise:cdi-api` | groupId/artifactId 与属性前缀不同 |
| `javax.jsf-api.version` | `javax.faces:jsf-api` | artifactId 无 `javax.` 前缀 |
| `javax.validation-api.version` | `javax.validation:validation-api` | 同上 |
| `jsr305.version` | `com.google.code.findbugs:jsr305` | 根本不是 `javax.*` 坐标 |

重命名时请把属性名当作**半公开 API**：消费者可能引用它们；对 BOM 内部只是补丁级噪音，对外则是破坏性变更 ——
宁增不改。

## 4. 为什么不在此做 Jakarta 迁移

Jakarta EE 9+ 把每个规范重新打包（`javax.servlet.*` → `jakarta.servlet.*`），改变的是**坐标与类名** —— 那是另一个
契约，不是版本升级。本 BOM 冻结在 Java EE 8 集合，正是 `javax.*` 系的 Microsphere 模块得以可复现构建的前提。
兄弟 `jakarta` BOM 应当是*新模块*，而非本模块的升级。

## 5. 维护要点

- 内容接近静止（Java EE 8 已是终版）；本仓库的日常升级 PR 大多落在 logging/testing/javadb/tomcat。
- `2.0.1.Final`、`2.0.SP1` 这类版本号遵循 JCP 发布命名，原样保留。
- 若生态将来需要 Jakarta EE，**不要**原地替换条目：新增领域 BOM，并决定 `microsphere-all-bom` 是否同时导入两者
（当前只导入本模块）。

## 相关文档

- 消费者契约：[../../user-guide/zh/javaee-bom.md](../../user-guide/zh/javaee-bom.md)
- `javax`→`jakarta` 分界线真正起作用的地方：[tomcat-bom.md](tomcat-bom.md)
