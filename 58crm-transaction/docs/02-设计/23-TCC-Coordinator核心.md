# TCC Coordinator 核心

> 读完你能做什么：知道 C-003.2.1 的 Begin / RegisterBranch / ReportBranch 如何落 MySQL，以及并发幂等边界。

## 1. 范围

```text
crm-tx-tcc-coordinator
  begin
  registerBranch
  reportBranch
```

不包含：

```text
commit / rollback
TC -> RM callback
Scheduler / owner lease 推进
gRPC server
```

## 2. 模块结构

```text
com.wuba.crm.tx.tcc.coordinator
├── persistence
│   ├── TccGlobalEntity
│   ├── TccBranchEntity
│   ├── TccGlobalMapper
│   ├── TccBranchMapper
│   ├── TccGlobalRepository
│   ├── TccBranchRepository
│   ├── MybatisTccGlobalRepository
│   └── MybatisTccBranchRepository
└── service
    └── TccCoordinatorService
```

依赖：

```text
crm-tx-tcc-api
crm-tx-tcc-id
MyBatis-Plus
MySQL
```

## 3. Begin

流程：

```text
1. 按 requestId 查询
2. 已存在 -> 校验 bizCode/bizId -> 返回已有 XID
3. 不存在 -> TccXidGenerator.nextXid()
4. INSERT t_tcc_global
5. UNIQUE(request_id) 冲突 -> 加锁读胜者 -> 返回胜者 XID
```

为什么冲突后要用加锁读：

```text
MySQL REPEATABLE READ 下普通 SELECT 是快照读
两个实例并发 Begin 时，失败方可能看不到胜者已提交的行
SELECT ... FOR UPDATE 是当前读，能看到最新提交
```

XID 规则：

```text
XID 由 crm-tx-tcc-id 号段生成
XID > 0
XID 不复用
requestId 重复只复用同一笔事务
```

## 4. RegisterBranch

流程：

```text
1. SELECT t_tcc_global ... FOR UPDATE
2. global 必须是 TRYING
3. 按 (xid, resource_key) 查已有分支
4. 已有 -> 校验 actionName/callbackEndpoint/applicationData -> 返回同一 branchId
5. 没有 -> branchId = global.branch_seq + 1
6. UPDATE global.branch_seq
7. INSERT t_tcc_branch
```

关键：

```text
branch_seq 分配在 global 行锁内完成
同一 XID 的并发注册不会拿到重复 branchId
branchId 允许空洞，不回退
```

幂等冲突：

```text
同 resourceKey，定义一致 -> 返回已有 branch
同 resourceKey，action/endpoint/appData 不一致 -> TCC_INVALID_STATE
```

## 5. ReportBranch

SQL：

```sql
UPDATE t_tcc_branch
SET status = ?, updated_at = ?
WHERE xid = ?
  AND branch_id = ?
  AND status = 'TRYING'
  AND EXISTS (
      SELECT 1 FROM t_tcc_global g
      WHERE g.xid = ? AND g.status = 'TRYING'
  )
```

语义：

```text
global TRYING + branch TRYING -> 更新
已是同一结果 -> 幂等成功
冲突结果 -> TCC_INVALID_STATE
branch 不存在 -> TCC_NOT_FOUND
global 已进入二阶段 -> TCC_INVALID_STATE
```

ReportBranch 不加 owner：

```text
它只落 Try 结果，不推进二阶段
owner 只用于 Commit/Cancel
```

## 6. 事务边界

```text
Begin            @Transactional
RegisterBranch   @Transactional + global FOR UPDATE
ReportBranch     @Transactional + 条件 UPDATE
```

没有跨库事务：

```text
Coordinator 只写自己的 MySQL
RM 业务库由 participant 自己保证
```

## 7. 错误码

```text
TCC_NOT_FOUND
  global/branch 不存在

TCC_INVALID_STATE
  幂等键复用但业务标识不同
  global 不是 TRYING
  branch 上报冲突
  分支定义冲突
```

## 8. 测试

```text
Begin:
  正整数 XID
  requestId 幂等
  同 requestId 不同 bizId 拒绝
  8 线程并发 Begin 只产生一个 XID

RegisterBranch:
  branchId 连续
  同 resourceKey 幂等
  同 resourceKey 不同 action 拒绝
  8 线程并发注册 branchId 不重复

ReportBranch:
  TRY_SUCCESS 落地
  重复上报幂等
  冲突上报拒绝
  未知 branch 返回 NOT_FOUND
```

## 9. 未做

```text
Commit/Rollback
owner lease
next_retry_at 推进
RM callback
gRPC codegen/server
```

## 10. 一句话

> **Begin 靠 requestId 唯一键和加锁读保证幂等，RegisterBranch 靠 global 行锁保证 branch_seq，ReportBranch 只在 global/branch 都是 TRYING 时落 Try 结果。**
