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
TC -> RM confirm
RM 返回明确业务失败
TC retry 达上限
branch DEAD
global FAILED
告警人工
```

## N. TC 崩溃在 CONFIRMING

```text
old TC 崩溃
lease 到期
new TC 抢 owner
扫描 CONFIRMING branch
重试 Confirm
```

## O. RM 崩溃在 Confirm 后

```text
RM barrier CONFIRMED
响应丢失
TC 重试
RM 重启
barrier CONFIRMED -> 幂等
```

## P. Confirm 重复

```text
TC 重发 Confirm
RM barrier CONFIRMED
直接返回成功
不执行业务
```

## Q. Cancel 全部成功

```text
TC -> RM1 cancel -> CANCELLED
TC -> RM2 cancel -> CANCELLED
global CANCELLED
```

## R. Cancel 部分失败

```text
RM1 CANCELLED
RM2 失败 -> CANCELLING -> retry
RM2 CANCELLED
global CANCELLED
```

## S. Cancel 响应丢失

```text
RM barrier CANCELLED
响应丢失
TC 重试
RM 幂等成功
```

## T. Cancel 无 barrier

```text
TC -> RM cancel
RM barrier 无记录
INSERT SUSPENDED
返回成功
```

## U. Try 后到

```text
TC -> RM cancel -> SUSPENDED
late Try -> RM
RM barrier SUSPENDED
拒绝 Try
```

## V. TC 崩溃在 CANCELLING

```text
old TC 崩溃
lease 到期
new TC 抢 owner
继续 Cancel
```

## W. RM 崩溃在 Try 前

```text
barrier 无记录
TC timeout -> Cancel
Cancel -> SUSPENDED
```

## X. RM 崩溃在 Try 后

```text
barrier TRIED
TC timeout -> Cancel
Cancel -> CANCELLED
```

## Y. Try/Cancel 并发

```text
Try 先插入 TRIED -> Cancel 释放 -> CANCELLED
Cancel 先插入 SUSPENDED -> Try 拒绝
```

## Z. callback 注册 tc-0、commit tc-3

```text
TM -> tc-0 registerBranch
       INSERT callbackEndpoint
TM -> tc-3 commit
       SELECT callbackEndpoint
       tc-3 -> RM confirm
```

分析：

```text
branch 业务不在 TC 执行
tc-3 只读 DB 和回调 RM
不需要 sticky
```

## AA. callback endpoint 不可达

```text
tc-3 -> RM callback
连接失败/超时
branch 保持 CONFIRMING
retry_count + 1
next_retry_at
超限 -> DEAD / global FAILED
```

## AB. Duplicate Try

```text
RM barrier TRIED
重复 Try
返回成功
不重复预占
```

## AC. Confirm/Cancel 并发

```text
Confirm 先拿到 barrier 行锁
  -> CONFIRMED
Cancel 后到
  -> 看到 CONFIRMED，拒绝
```

反向：

```text
Cancel 先拿到行锁
  -> CANCELLED
Confirm 后到
  -> 看到 CANCELLED，拒绝
```

## 状态图

### global

```text
TRYING -> CONFIRMING -> CONFIRMED
   |          |
   v          v
CANCELLING -> CANCELLED
   |
   v
FAILED
```

### branch

```text
TRYING -> TRY_SUCCESS -> CONFIRMING -> CONFIRMED
   |          |               |
   |          |               v
   |          |             DEAD
   |          v
   |       CANCELLING -> CANCELLED
   |               |
   |               v
   |             SUSPENDED
   v
TRY_FAIL -> CANCELLING
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
28 个独立 case
每个 case 独立时序 + 分析
branch 不在 TC 执行
跨 TC 只影响状态读写和 endpoint 路由
```
