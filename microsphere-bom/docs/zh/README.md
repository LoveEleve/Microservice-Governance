# Microsphere BOM 文档

本目录包含 [Microsphere BOM](https://github.com/microsphere-projects/microsphere-bom) 项目的完整文档。
Microsphere BOM 是一组 Bill of Materials（BOM）POM，用于集中管理 Microsphere 生态系统的第三方依赖版本。

> The English documentation index is at [docs/README.md](../README.md).

## 文档体系

| 文档                | 读者            | 内容                                                         |
|---------------------|-----------------|--------------------------------------------------------------|
| [用户文档](../current/user-guide/zh/README.md)     | BOM 制品的使用者 | 各 BOM 的导入方式、托管依赖清单、代码示例、版本信息与兼容范围 |
| [开发者文档](../current/developer-guide/zh/README.md) | 维护者与贡献者  | 架构设计、源码分析、设计模式、构建与发布流程（含流程图）      |

## 按分支组织的文档

文档仅针对 release 分支维护：

| 分支            | 文档目录              | 状态 |
|-----------------|-----------------------|------|
| `release`       | [`current/`](../current/README.md) | 最新发布版本线（0.3.x；最新已发布版本 **0.3.16**） |
| `release-1.x`   | `1.x/` | **未创建** —— 本仓库不存在 `release-1.x` 分支；若该分支创建后再补充此目录。 |

## 目录结构

中文文档与英文文档同目录并存，英文为主路径、中文位于各指南目录下的 `zh/` 子目录，文件名一一对应。

```
docs/
├── README.md                    # 英文总索引
├── zh/
│   └── README.md                # 中文总索引（本文件）
└── current/                     # release 分支文档
    ├── user-guide/              # 英文用户文档（每个 Maven 模块一篇）
    │   └── zh/                  # 中文用户文档（文件名与英文一致）
    └── developer-guide/         # 英文开发者文档
        └── zh/                  # 中文开发者文档（文件名与英文一致）
```

## 版本信息

- **BOM 制品版本线**：`0.3.x`（Maven Central groupId：`io.github.microsphere-projects`）。
- **Java 兼容范围**：JDK 8、11、17、21、25（CI 基于 Temurin 验证）。各库的 JDK 规则详见
  [用户文档 — 版本与兼容性](../current/user-guide/zh/versions.md)。

## 许可证

所有文档基于 [Apache License 2.0]../../LICENSE 发布。
