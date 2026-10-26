# microsphere-testing-bom — 源码分析

> 本文属于 [Microsphere BOM 开发者文档](README.md) · 英文版见 [../testing-bom.md](../testing-bom.md)

模块源码：[`microsphere-testing-bom/pom.xml`](../../../../microsphere-testing-bom/pom.xml)

## 1. POM 骨架

```
<parent>       microsphere-bom:${revision}
<properties>   6 个 *.version
<dependencyManagement>
    junit:junit                          ← 直接钉版（声明在 import 之前，见 §3）
    org.junit:junit-bom        [import]  ← 嵌套 BOM #1
    org.mockito:mockito-bom    [import]  ← 嵌套 BOM #2
    jsonassert / hamcrest / jmh-core / jmh-generator-annprocess  ← 直接钉版
<profiles>     java8-16
```

## 2. 嵌套 BOM 模式（委托）

这是唯一采用**委托**而非逐一枚举的领域 BOM：

```xml
<dependency>
    <groupId>org.junit</groupId>
    <artifactId>junit-bom</artifactId>
    <version>${junit-jupiter.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

关键机制：

- 属性 `junit-jupiter.version` 同时充当**上游 BOM 自身的坐标版本** —— 一个旋钮管理整个 Jupiter + Platform 制品族
  （jupiter-api/engine/params、聚合件 `junit-jupiter`、vintage engine、platform-launcher…）。手工罗列这约 15 个
  制品每逢 JUnit 发布都会漂移。
- Mockito 同理，经 `mockito.version` → `mockito-bom`（core、junit-jupiter 扩展、inline…）。
- **属性在*本* POM 的模型内解析**，所以 `java8-16` profile 能够重定向嵌套 import —— profile 无法改写传递 BOM 内部
  的属性，但它*可以*选择*导入哪个* BOM：

```mermaid
flowchart TD
    A[解析 junit-jupiter.version] --> B{JDK 在 [1.8,17)？}
    B -- 是 --> C[5.14.4]
    B -- 否 --> D[6.1.3]
    C --> E[导入 org.junit:junit-bom:5.14.4]
    D --> F[导入 org.junit:junit-bom:6.1.3]
    E & F --> G[所有 jupiter-* / platform-* 坐标<br/>获得该制品族的版本]
```

## 3. 顺序与优先级

`dependencyManagement` 列表顺序：`junit:junit`（直接）→ `junit-bom`（import）→ `mockito-bom`（import）→ 直接钉版。
按 Maven 的**先声明者优先**规则：

- 显式 `junit:junit 4.13.2` 声明在 `junit-bom` import **之前**，即使未来上游 BOM 开始托管该坐标，显式条目仍然胜出。
- 被导入的两个 BOM 之间不存在坐标冲突（JUnit 与 Mockito 制品族互不相交）。

## 4. Profile：`java8-16`

```xml
<profile>
    <id>java8-16</id>
    <activation><jdk>[1.8,17)</jdk></activation>
    <properties>
        <junit-jupiter.version>5.14.4</junit-jupiter.version>
        <mockito.version>4.11.0</mockito.version>
    </properties>
</profile>
```

- 一个 profile、两处回退 —— 两个库在 Java-8 / 11–16 上面临相同的兼容悬崖（JUnit 6 需要 17；Mockito 5 需要 11，
  且本项目仅在 17+ 上测试它）。
- 名称 `java8-16` 表达意图（JDK 区间），范围 `[1.8,17)` 编码该意图。
- Hamcrest/JMH/JSONassert/JUnit 4 在这些版本上与 JDK 无关 —— 无需 profile 条目。

## 5. 本模块在他处的印证

`microsphere-bom-example` 以**无版本**方式依赖 `org.junit.jupiter:junit-jupiter` —— 聚合件 `junit-jupiter` 之所以
可用，完全因为导入了 `junit-bom`。CI 在全部五个 JDK 上运行 `ExampleServiceTest`，因此"嵌套 BOM + profile"机制是
被真实执行过的，而不只是声明。用法见 [bom-example](../../user-guide/zh/bom-example.md)，reactor 校验方式见
[build-system.md](build-system.md)。

## 相关文档

- 面向使用者的契约：[../../user-guide/zh/testing-bom.md](../../user-guide/zh/testing-bom.md)
- 嵌套 BOM 模式全局视角：[architecture.md](architecture.md)
