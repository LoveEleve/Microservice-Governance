# C-003.2：Coordinator 核心

## 用户故事

作为 TM，我可以开启全局事务、注册分支、上报 Try 结果，Coordinator 把状态可靠写入 MySQL。

## 范围

```text
crm-tx-tcc-coordinator 模块
t_tcc_global / t_tcc_branch 实体与 Mapper
Begin
RegisterBranch
ReportBranch
XID 接入 crm-tx-tcc-id
```

## 不包含

```text
Commit / Rollback（C-003.3）
TC -> RM callback（C-003.3）
Barrier（C-003.4）
Scheduler / owner retry（C-003.6）
gRPC server（C-003.2.2）
```

## 验收

```text
AC1 Begin 生成正整数 XID，写 t_tcc_global
AC2 重复 requestId 返回同一 XID
AC3 RegisterBranch 原子递增 branch_seq，Try 超时后拒绝
AC4 重复 resourceKey 返回同一 branch
AC5 ReportBranch 只接受 TRY_SUCCESS / TRY_FAIL
AC6 重复上报同状态幂等
AC7 冲突上报返回 TCC_INVALID_STATE
AC8 所有写操作在 MySQL 事务内
```

## 测试

```text
单元：状态/校验
集成：H2 MySQL 模式 + MyBatis-Plus
并发：重复 register 不产生重复 branch
```

## 文档

```text
docs/02-设计/23-TCC-Coordinator核心.md
```


## 完成记录

```text
TccCoordinatorService
TccGlobalEntity / TccBranchEntity
TccGlobalMapper / TccBranchMapper
Mybatis repositories
Begin / RegisterBranch / ReportBranch
```

验证：

```text
mvn -q test
tests=194 failures=0 errors=0 skipped=1
```

C-003.2.2 继续：

```text
gRPC codegen
TccCoordinatorGrpc adapter
DTO <-> proto mapping
error code mapping
```
