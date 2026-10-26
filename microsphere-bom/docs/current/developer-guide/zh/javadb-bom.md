# microsphere-javadb-bom — 源码分析

> 本文属于 [Microsphere BOM 开发者文档](README.md) · 英文版见 [../javadb-bom.md](../javadb-bom.md)

模块源码：[`microsphere-javadb-bom/pom.xml`](../../../../microsphere-javadb-bom/pom.xml)

## 1. POM 骨架

最小的领域 BOM —— 两个属性、两条托管、一个 profile：

```
<properties>   sqlite-jdbc.version=3.53.4.0 · h2.version=2.5.252
<dependencyManagement>  org.xerial:sqlite-jdbc · com.h2database:h2
<profiles>     java8 → h2.version=1.4.200
```

## 2. 为什么只有两个制品

两者都是**自包含 JDBC 驱动**（SQLite 内嵌各平台原生库；H2 纯 Java），没有需要锁步的同族制品，因此无需制品族
分组。本模块说明：领域边界按*使用场景*（"开发/测试用嵌入式数据库"）划分，而非按制品数量。

## 3. Profile：`java8` —— `(,1.8]` 范围

```xml
<activation><jdk>(,1.8]</jdk></activation>
```

- 开下界 `(,` 意为"任意 ≤ 1.8" —— 与其它 profile 不同，这个也覆盖 Java 5–7 的构建环境。
- H2 2.x 要求 Java 8+；钉住 1.4.200 作为 1.4.x 的最后一个版本（注意：1.4.200 *就是* 1.4 线的终点补丁）。
- 有意的不对称：`sqlite-jdbc` 无需回退（3.x 线兼容 Java 8，更旧的 xerial 版本也支持 Java 6/7）。

## 4. 维护要点

- SQLite JDBC 采用 xerial 的四段式 `大版本.年.月.补丁` 风格版本号 —— Dependabot 按普通递增处理；尾部的 `.0`
  是版本的一部分，不可省略。
- H2 跨 1.4 → 2.x 升级对既有 `.mv.db` 文件的消费者是**破坏性变更**；profile 机制有意把 Java 8 时代的构建与这次
  迁移隔离开。

## 相关文档

- 消费者契约：[../../user-guide/zh/javadb-bom.md](../../user-guide/zh/javadb-bom.md)
- 属性覆盖模式：[architecture.md](architecture.md) §6
