# TCC 时序与状态图

> 读完你能做什么：逐个 case 理解 TCC 的正常、失败、超时、注册失败、报告丢失、崩溃、空回滚、悬挂、重试、callback 跨 TC。

## 0. 先说清楚：branch 不在 TC 执行

TC 不执行 branch 业务。

```text
TC：只维护 global/branch 状态，发起 Confirm/Cancel
RM：真正执行 Try/Confirm/Cancel 和 barrier
```

所以：

```text
branchRegister 到 tc-0
commit 到 tc-3
```

不会有问题，因为：

```text
branch 行在 MySQL
callbackEndpoint 在 branch 行
tc-3 从 DB 读 endpoint
tc-3 调 RM
业务仍在 RM 执行
```

不需要“把 branch 搬到 tc-3 执行”。

## 1. owner_token / lease

```text
t_tcc_global.owner_token
t_tcc_global.lease_until
```

它表示：

```text
当前哪个 TC 实例有权推进这个 xid
```

抢租约：

```sql
UPDATE t_tcc_global
SET owner_token = ?,
    lease_until = ?
WHERE xid = ?
  AND status IN ('TRYING','CONFIRMING','CANCELLING')
  AND (lease_until IS NULL OR lease_until < NOW());
```

第一版只放 global：

```text
一个 xid 同时只有一个 TC owner
owner 处理该事务所有 branch
branch 不单独抢 lease
```

owner 崩溃：

```text
lease 到期
其他 TC 抢到 owner
从 DB 恢复 branch 状态继续
```

## 2. Case 目录

```text
A. 正常成功
B. Try 失败
C. Try 超时/结果未知
D. branchRegister 失败
E. branchRegister 响应丢失
F. Try 成功但 report 丢失
G. TM 崩溃在 begin 后
H. TM 崩溃在部分 Try 后
I. global 超时
J. Confirm 全部成功
K. Confirm 部分失败
L. Confirm 响应丢失
M. Confirm 业务永久失败
N. TC 崩溃在 CONFIRMING
O. RM 崩溃在 Confirm 后
P. Confirm 重复
Q. Cancel 全部成功
R. Cancel 部分失败
S. Cancel 响应丢失
T. Cancel 无 barrier
U. Try 后到
V. TC 崩溃在 CANCELLING
W. RM 崩溃在 Try 前
X. RM 崩溃在 Try 后
Y. Try/Cancel 并发
Z. callback 注册 tc-0、commit tc-3
AA. callback endpoint 不可达
AB. Duplicate Try
AC. Confirm/Cancel 并发
AD. commit 时仍有 branch TRYING
AE. commit 时 branch TRY_FAIL
AF. owner lease 生命周期
AG. commit/rollback 落到非 owner TC
AH. global FAILED 人工处理
```

## A. 正常成功

```text
TM              RM1             RM2              TC
 |-- begin ------------------------------------>|
 |<-------------------------------- xid ---------|
 |-- registerBranch(RM1) ---------------------->|
 |<---------------------------- branchId1 -------|
 |-- registerBranch(RM2) ---------------------->|
 |<---------------------------- branchId2 -------|
 |-- try(branch1)->|              |              |
 |                |-- barrier TRIED              |
 |                |-- reserve                   |
 |<-- ok ---------|              |              |
 |-- report SUCCESS(branch1) ------------------>|
 |-- try(branch2) -------------->|              |
 |                |              |-- barrier TRIED
 |                |              |-- reserve
 |<-- ok ------------------------|              |
 |-- report SUCCESS(branch2) ------------------>|
 |-- commit ----------------------------------->|
 |                |<-- confirm(branch1) ---------|
 |                |-- barrier CONFIRMED          |
 |                |-- final + outbox             |
 |                |-- ok ----------------------->|
 |                |              |<-- confirm(branch2)
 |                |              |-- barrier CONFIRMED
 |                |              |-- final + outbox
 |                |              |-- ok -------->|
 |<-------------------------------- CONFIRMED ---|
```

分析：

```text
register 先于 Try
Try 只预占
Confirm 才写终态和 Outbox
所有 branch CONFIRMED 后 global CONFIRMED
```

## B. Try 失败

```text
TM              RM1             RM2              TC
 |-- try RM1 --->|              |              |
 |<-- ok --------|              |              |
 |-- report SUCCESS -------------------------->|
 |-- try RM2 ------------------>|              |
 |<-- fail ---------------------|              |
 |-- report TRY_FAIL ------------------------>|
 |-- rollback -------------------------------->|
 |                |<-- cancel -----------------|
 |                |-- barrier CANCELLED         |
 |                |-- ok ---------------------->|
 |                |              |<-- cancel ----|
 |                |              |-- no barrier -> SUSPENDED
 |<-------------------------------- CANCELLED --|
```

分析：

```text
RM2 没有预占成功，Cancel 走空回滚
RM1 已 Try 成功，Cancel 释放
global CANCELLED
```

## C. Try 超时/结果未知

```text
TM              RM1             TC
 |-- try -------->|              |
 |                |-- barrier TRIED
 |   RPC timeout  |              |
 |-- rollback ----------------->|
 |                |<-- cancel ---|
 |                |-- barrier TRIED -> CANCELLED
 |<-------------------------------- CANCELLED
```

分析：

```text
超时不能判定 Try 未发生
必须 rollback
barrier TRIED 则释放
barrier 无记录则 SUSPENDED
```

## D. branchRegister 失败

```text
TM              RM1             TC
 |-- registerBranch ---------->|
 |<-- error -------------------|
 |-- 不调用 Try  |              |
```

分析：

```text
TC 未登记成功
没有资源
不需要 Cancel
TM 直接失败
```

## E. branchRegister 响应丢失

```text
TM              TC
 |-- registerBranch ---------->|
 |                    DB 已写入，响应丢失
 |-- registerBranch(retry) --->|
 |<-- 返回已有 branchId -------|
```

分析：

```text
UNIQUE(xid, resource_key)
重复 register 幂等
不能创建第二个分支
```

## F. Try 成功但 report 丢失

```text
TM              RM1             TC
 |-- try -------->|              |
 |                |-- barrier TRIED
 |                |-- 预占成功
 |-- report SUCCESS ----------->|
 |                |        DB 未写入/响应丢失
 |-- rollback ----------------->|
 |                |<-- cancel ---|
 |                |-- barrier TRIED -> CANCELLED
```

分析：

```text
TC branch 仍是 TRYING
不能进入 CONFIRMING
只能超时 CANCELLING
```

## G. TM 崩溃在 begin 后

```text
TM              TC
 |-- begin ----->|
 |  TM crash     |
 |               |-- timeout_at 到期
 |               |-- CANCELLING
 |               |-- 无 branch
 |               |-- CANCELLED
```

分析：

```text
没有任何资源
global 仍要收敛
```

## H. TM 崩溃在部分 Try 后

```text
TM              RM1             RM2             TC
 |-- try RM1 --->|              |              |
 |<-- ok ---------|              |              |
 |-- report SUCCESS -------------------------->|
 |-- try RM2 ------------------>|              |
 |  TM crash     |              |              |
 |               |              |-- barrier TRIED
 |               |              |-- reserve
 |               |              |              |
 |               |              |<-- timeout cancel
 |               |<-- cancel ----|              |
 |               |-- release     |              |
 |               |              |-- release / SUSPENDED
 |<-------------------------------- CANCELLED --|
```

分析：

```text
TM 崩了不会 commit
TC 超时后取消
RM2 的 Try 即使成功也会被 Cancel
```

## I. global 超时

```text
TM              TC
 |-- begin/partial try
 |  no commit    |
 |               |-- timeout_at 到期
 |               |-- CANCELLING
 |               |-- 逐 branch Cancel
 |<-------------------------------- CANCELLED
```

## J. Confirm 全部成功

```text
TC              RM1             RM2
 |-- confirm --->|              |
 |               |-- CONFIRMED  |
 |<-- ok ---------|              |
 |-- confirm ------------------>|
 |               |              |-- CONFIRMED
 |<-- ok -----------------------|
 |-- global CONFIRMED
```

## K. Confirm 部分失败

```text
TC              RM1             RM2
 |-- confirm --->|              |
 |<-- ok ---------|              |
 |-- confirm ------------------>|
 |<-- fail ---------------------|
 |-- 保持 CONFIRMING
 |-- retry RM2
 |-- ok ------------------------>|
 |-- global CONFIRMED
```

分析：

```text
只重试 RM2
RM1 不重复执行
global 不提前 CONFIRMED
```

## L. Confirm 响应丢失

```text
TC -> RM confirm
RM 本地事务已 CONFIRMED
响应丢失
TC 重试 confirm
RM barrier CONFIRMED
-> 直接返回成功
```

分析：

```text
Confirm 幂等
Outbox eventKey 幂等
```

## M. Confirm 业务永久失败

```text
TC              RM2
 |-- confirm --->|
 |               |-- barrier TRIED
 |               |-- 业务返回 PERMANENT_FAILURE
 |<-- permanent -|
 |-- branch DEAD
 |-- global FAILED
 |-- 告警人工
```

分析：

```text
只有明确的永久失败才 DEAD
网络超时/临时失败仍走重试
DEAD 后停止自动 Confirm
```

## N. TC 崩溃在 CONFIRMING

```text
tc-0           MySQL              tc-1
 |-- owner lease
 |-- confirm RM1 -> CONFIRMED
 |-- confirm RM2 -> 处理中
 |  crash
 |                               |-- lease 到期
 |                               |-- 抢 owner
 |                               |-- 读 global=CONFIRMING
 |                               |-- 读 branch2=CONFIRMING
 |                               |-- 重试 Confirm
 |                               |-- global CONFIRMED
```

分析：

```text
新 TC 不重做 RM1
只处理未终态 branch
owner lease 防止 tc-0/tc-1 同时推进
```

## O. RM 崩溃在 Confirm 后

```text
TC              RM2
 |-- confirm --->|
 |               |-- BEGIN
 |               |-- barrier TRIED -> CONFIRMED
 |               |-- 业务终态 + Outbox
 |               |-- COMMIT
 |               |  crash before response
 |<-- timeout ---|
 |-- retry
 |               |-- RM 重启
 |<-- CONFIRMED -|
 |-- global CONFIRMED
```

分析：

```text
Confirm 已提交，响应丢失
重试通过 barrier 幂等
Outbox eventKey 防重复
```

## P. Confirm 重复

```text
TC              RM2
 |-- confirm --->|
 |               |-- barrier CONFIRMED
 |               |-- 不执行业务
 |<-- success ---|
```

分析：

```text
Confirm 重发直接成功
不能再次更新业务/Outbox
```

## Q. Cancel 全部成功

```text
TC              RM1             RM2
 |-- cancel ---->|              |
 |               |-- barrier CANCELLED
 |<-- ok ---------|              |
 |-- cancel ------------------->|
 |               |              |-- barrier CANCELLED
 |<-- ok -----------------------|
 |-- global CANCELLED
```

## R. Cancel 部分失败

```text
TC              RM1             RM2
 |-- cancel ---->|              |
 |<-- ok ---------|              |
 |-- cancel ------------------->|
 |<-- fail ---------------------|
 |-- 保持 CANCELLING
 |-- retry RM2
 |<-- ok -----------------------|
 |-- global CANCELLED
```

分析：

```text
RM1 不重复释放
RM2 继续重试
超限 -> branch DEAD/global FAILED
```

## S. Cancel 响应丢失

```text
TC              RM2
 |-- cancel ---->|
 |               |-- barrier CANCELLED
 |               |  response lost
 |<-- timeout ---|
 |-- retry
 |<-- success ---|
```

## T. Cancel 无 barrier

```text
TC              RM2
 |-- cancel ---->|
 |               |-- BEGIN
 |               |-- 无 barrier
 |               |-- INSERT SUSPENDED
 |               |-- COMMIT
 |<-- success ---|
```

分析：

```text
空回滚
不释放不存在的资源
持久 SUSPENDED 防迟到 Try
```

## U. Try 后到

```text
TC              RM2
 |-- cancel ---->|
 |               |-- SUSPENDED
 |<-- success ---|
 |               |
 |-- late try --->|
 |               |-- barrier SUSPENDED
 |               |-- 拒绝 Try
 |<-- rejected --|
```

分析：

```text
悬挂防护
迟到的 Try 不能重新预占
```

## V. TC 崩溃在 CANCELLING

```text
tc-0           MySQL              tc-1
 |-- owner lease
 |-- cancel RM1 -> CANCELLED
 |-- cancel RM2 -> 处理中
 |  crash
 |                               |-- lease 到期
 |                               |-- 抢 owner
 |                               |-- 读 global=CANCELLING
 |                               |-- 重试 Cancel RM2
 |                               |-- global CANCELLED
```

## W. RM 崩溃在 Try 前

```text
TM              RM2             TC
 |-- try ---------------------->|
 |  RM2 未执行
 |  crash
 |-- report timeout ----------->|
 |-- rollback ----------------->|
 |               |<-- cancel ----|
 |               |-- 无 barrier
 |               |-- SUSPENDED
 |<-------------------------------- CANCELLED
```

## X. RM 崩溃在 Try 后

```text
TM              RM2             TC
 |-- try ---------------------->|
 |               |-- barrier TRIED
 |               |-- 预占
 |               |  crash before report
 |-- report timeout ----------->|
 |-- rollback ----------------->|
 |               |<-- cancel ----|
 |               |-- barrier TRIED
 |               |-- CANCELLED
 |<-------------------------------- CANCELLED
```

## Y. Try/Cancel 并发

### Y1 Try 先拿到 barrier

```text
TC              RM2
 |-- try -------->|
 |               |-- INSERT TRIED 成功
 |               |-- 预占
 |<-- ok ---------|
 |-- cancel ----->|
 |               |-- SELECT FOR UPDATE
 |               |-- TRIED -> CANCELLED
 |               |-- 释放
 |<-- cancelled -|
```

### Y2 Cancel 先拿到 barrier

```text
TC              RM2
 |-- cancel ---->|
 |               |-- 无 barrier
 |               |-- INSERT SUSPENDED
 |<-- success ---|
 |-- try -------->|
 |               |-- INSERT 冲突
 |               |-- 看到 SUSPENDED
 |               |-- 拒绝 Try
 |<-- rejected --|
```

## Z. callback 注册 tc-0、commit tc-3

```text
TM -> tc-0 registerBranch
       INSERT t_tcc_branch(callbackEndpoint)
       return branchId

TM -> tc-3 commit
       SELECT branch FROM MySQL
       read callbackEndpoint
       tc-3 -> RM confirm
       RM -> tc-3 result
```

分析：

```text
branch 业务不在 TC 执行
tc-3 只读 DB + 回调 RM
不需要 sticky
```

## AA. callback endpoint 不可达

```text
tc-3 -> RM callback
       connect timeout
branch CONFIRMING
retry_count + 1
next_retry_at = now + backoff

retry...
达到上限:
  branch DEAD
  global FAILED
  告警人工
```

## AB. Duplicate Try

```text
TM              RM2
 |-- try ------->|
 |               |-- barrier TRIED
 |               |-- 预占
 |<-- ok ---------|
 |-- try(retry) ->|
 |               |-- barrier TRIED
 |               |-- 不重复预占
 |<-- ok ---------|
```

## AC. Confirm/Cancel 并发

### AC1 Confirm 先拿行锁

```text
TC              RM2
 |-- confirm --->|
 |               |-- SELECT ... FOR UPDATE
 |               |-- TRIED -> CONFIRMED
 |<-- success ----|
 |-- cancel ----->|
 |               |-- 看到 CONFIRMED
 |               |-- 拒绝 Cancel
 |<-- rejected ---|
```

### AC2 Cancel 先拿行锁

```text
TC              RM2
 |-- cancel ---->|
 |               |-- SELECT ... FOR UPDATE
 |               |-- TRIED -> CANCELLED
 |<-- success ----|
 |-- confirm ---->|
 |               |-- 看到 CANCELLED
 |               |-- 拒绝 Confirm
 |<-- rejected ---|
```

## AD. commit 时仍有 branch TRYING

```text
TM              TC
 |-- commit ---->|
 |               |-- 发现 branch TRYING
 |<-- TCC_NOT_READY
 |-- 等待 report / rollback
```

分析：

```text
不能进入 CONFIRMING
TM 不能跳过未完成的 Try
```

## AE. commit 时 branch TRY_FAIL

```text
TM              TC
 |-- commit ---->|
 |               |-- 发现 TRY_FAIL
 |               |-- global CANCELLING
 |               |-- Cancel 其他分支
 |<-------------------------------- CANCELLED
```

## AF. owner lease 生命周期

```text
tc-0            MySQL
 |-- UPDATE owner_token/lease_until
 |-- 抢到 owner
 |-- 处理 branch
 |-- 续约 lease
 |  crash（未释放）
 |               |-- lease 到期
tc-1
 |-- UPDATE owner_token/lease_until
 |-- 抢到 owner
 |-- 从 DB 恢复
 |-- 旧 tc-0 即使恢复，也不能再更新
```

分析：

```text
所有 global/branch 状态更新必须带 owner_token + lease 条件
旧 owner 恢复后 update 影响行数为 0
```

## AG. commit/rollback 落到非 owner TC

```text
TM -> tc-3 commit
       tc-3 发现 owner 是 tc-0
       -> 尝试抢 owner / 等待
       -> 抢到才推进
       -> 否则返回 TCC_BUSY 并重试
```

分析：

```text
不能因为请求到了 tc-3 就绕过 owner lease
否则 tc-0/tc-3 可能同时 Confirm
```

## AH. global FAILED 人工处理

```text
branch DEAD
  -> global FAILED
  -> 停止自动推进
  -> 告警
  -> 人工根据 branch/barrier 处理
```

## Review 2：深度 review 发现

### P0：状态更新必须带 owner fencing

```sql
UPDATE t_tcc_global
SET status = ?
WHERE xid = ?
  AND status = ?
  AND owner_token = ?
  AND lease_until > NOW();
```

```sql
UPDATE t_tcc_branch
SET status = ?
WHERE xid = ?
  AND branch_id = ?
  AND status = ?
  AND EXISTS (
    SELECT 1 FROM t_tcc_global
    WHERE xid = ? AND owner_token = ? AND lease_until > NOW()
  );
```

不加 owner 条件：

```text
旧 owner 恢复后仍可更新 branch
新 owner 可能被覆盖
```

### P0：commit/rollback 到非 owner TC

```text
请求落到哪个 TC 不重要
必须抢到 global owner 才能推进
抢不到返回 TCC_BUSY
不能直接 Confirm/Cancel
```

### P1：Y1/Y2 原图箭头顺序错误

已修：

```text
Y1：Try 先完成，再 Cancel
Y2：Cancel 先完成，再 Try
```

### P1：AF 原图“完成释放后 crash”矛盾

已修：

```text
crash 发生在处理中/续约前
lease 到期后新 owner 接管
```

### P1：还缺的 case

```text
重复 commit
重复 rollback
owner lease 续约失败
callback 回调与 owner 切换并发
branch DEAD 后人工 Confirm/Cancel
```

### P1：FAILED 语义

```text
FAILED 是人工终态
不再自动推进
不代表其他 branch 已全部回滚/确认
```

## 状态图

### global

```text
TRYING
  | all Try success + commit
  v
CONFIRMING
  | all CONFIRMED
  v
CONFIRMED

TRYING
  | Try fail/timeout
  v
CANCELLING
  | all CANCELLED/SUSPENDED
  v
CANCELLED

CONFIRMING
  | retry exhausted
  v
FAILED

CANCELLING
  | retry exhausted
  v
FAILED
```

### branch

```text
TRYING
  | Try success
  v
TRY_SUCCESS
  | commit
  v
CONFIRMING
  | Confirm success
  v
CONFIRMED

CONFIRMING
  | retry exhausted
  v
DEAD

TRYING
  | Try fail
  v
TRY_FAIL
  | rollback
  v
CANCELLING
  | Cancel success
  v
CANCELLED

TRYING
  | cancel/timeout
  v
CANCELLING

CANCELLING
  | no barrier
  v
SUSPENDED

CANCELLING
  | retry exhausted
  v
DEAD
```

### barrier

```text
no row --Cancel--> SUSPENDED
TRIED --Confirm--> CONFIRMED
TRIED --Cancel--> CANCELLED
CONFIRMED --Cancel--> reject
SUSPENDED/CANCELLED --Try--> reject
```

## 结论

```text
当前完整时序图：A~AH 共 34 个 case
每个 case 独立图 + 分析
branch 不在 TC 执行
跨 TC 只影响状态读写和 callback 路由
状态图已补 CONFIRMING/CANCELLING -> FAILED
CANCELLING -> DEAD
TRYING -> CANCELLING
```
