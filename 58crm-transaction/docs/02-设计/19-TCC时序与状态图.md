# TCC 时序与状态图

> 读完你能做什么：按图理解 TCC 的正常、失败、超时、崩溃、空回滚、悬挂、重试和 TC 多实例路由。

## 0. 参与者

```text
TM   业务发起方/事务模板
TC   Coordinator Server
RM1  Order TCC
RM2  Inventory TCC
```

TM 和 RM1 可以是同一个服务。

## 1. owner_token / lease 是什么

不是业务锁，是 TC 集群内“谁有权推进这个 xid”的租约。

```text
t_tcc_global.owner_token
t_tcc_global.lease_until
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

含义：

```text
owner_token  本次处理实例的唯一 token
lease_until  租约到期时间
```

第一版只放 global：

```text
一个 xid 同时只有一个 TC owner
owner 负责该事务所有 branch 的调度
branch 不再单独抢 owner
```

原因：

```text
避免同一 xid 被多个 TC 并发推进
branch 数量通常不大
后续 branch 多时再拆 branch lease
```

owner 崩溃：

```text
lease 到期
其他 TC 重新抢租约
根据 DB 中的 branch 状态继续
```

## 2. 先修正参考图的一个顺序

参考图：

```text
RM Try 本地事务
  -> fence TRIED
  -> branchRegister
```

风险：

```text
Try 已提交
TC 还没登记 branch
branchRegister 失败 -> TC 不知道这次预占
```

本方案：

```text
registerBranch 在 Try 之前
TC 先返回 branchId
再执行 Try
```

如果业务必须 RM 自己注册，也要求 RM 在本地 Try 事务之前完成 branchRegister，不能让预占先落库。

## 3. 正常成功

```text
TM(Order)          RM1(Order)             RM2(Inventory)          TC
   |                   |                        |                 |
   |-- begin ---------------------------------------------------->|
   |<----------------------------------------------- xid ---------|
   |-- registerBranch(RM1) -------------------------------------->|
   |<------------------------------------------ branchId1 ---------|
   |-- registerBranch(RM2) -------------------------------------->|
   |<------------------------------------------ branchId2 ---------|
   |-- try(branchId1) ->|                        |                 |
   |                   |-- barrier TRIED         |                 |
   |                   |-- order: NEW->ASSIGNING |                 |
   |<-- success --------|                        |                 |
   |-- report(TRY_SUCCESS, branchId1) ------------------------------>|
   |-- try(branchId2) -------------------------->|                 |
   |                   |                        |-- barrier TRIED |
   |                   |                        |-- reserved +1   |
   |<-- success ---------------------------------|                 |
   |-- report(TRY_SUCCESS, branchId2) ------------------------------>|
   |-- commit ----------------------------------------------------->|
   |                   |<-- confirm(branchId1) ---------------------|
   |                   |-- barrier CONFIRMED     |                 |
   |                   |-- order: ASSIGNED       |                 |
   |                   |-- outbox INIT           |                 |
   |                   |-- success ------------->|                 |
   |                   |                        |<-- confirm(branchId2)
   |                   |                        |-- barrier CONFIRMED
   |                   |                        |-- reserved->used
   |                   |                        |-- outbox INIT
   |<-------------------------------------------------- committed --|
```

分析：

```text
Try 先注册分支，再预占
Confirm 才写终态和 Outbox
TC 在全部 branch CONFIRMED 后置 global CONFIRMED
```

## 4. Try 失败

```text
TM                  RM1                    RM2                    TC
 |                   |                      |                     |
 |-- try RM1 ------->|                      |                     |
 |<-- success -------|                      |                     |
 |-- report SUCCESS ------------------------------>|
 |-- try RM2 -------------------------------->|                     |
 |<-- fail ----------------------------------|                     |
 |-- report TRY_FAIL -------------------------------------------->|
 |-- rollback --------------------------------------------------->|
 |                   |<-- cancel --------------------------------|
 |                   |-- barrier CANCELLED   |                     |
 |                   |-- release order       |                     |
 |                   |-- success ----------->|                     |
 |                   |                      |<-- cancel ----------|
 |                   |                      |-- no barrier -> SUSPENDED
 |                   |                      |-- success --------->|
 |<----------------------------------------------- CANCELLED -----|
```

分析：

```text
RM2 Try 失败，没有资源
Cancel 对 RM2 走空回滚 -> SUSPENDED
RM1 的资源被释放
global CANCELLED
```

## 5. Try 超时/结果未知

```text
TM                  RM1                    TC
 |                   |                     |
 |-- try ----------->|                     |
 |                   |-- 本地事务已提交    |
 |   超时/网络断      |                     |
 |-- rollback ---------------------------->|
 |                   |<-- cancel ----------|
 |                   |-- barrier TRIED     |
 |                   |-- release           |
 |                   |-- CANCELLED ------->|
 |<-------------------------------- CANCELLED
```

分析：

```text
TM 不知道 Try 是否成功
必须走 rollback
Cancel 看到 barrier TRIED 就释放
不能因为 RPC 超时直接判定 Try 没发生
```

## 6. branchRegister 失败

```text
TM                  RM1                    TC
 |                   |                     |
 |-- registerBranch -------------------->|
 |<-- error ------------------------------|
 |-- 不执行 Try     |                     |
```

分析：

```text
register 失败 -> 不产生资源
不需要 Cancel
如果 register 响应丢失但 TC 已写入
TM 重试同一个 resourceKey
UNIQUE(xid, resource_key) 返回已有 branch
```

## 7. Try 成功但 report 失败

```text
TM                  RM1                    TC
 |                   |                     |
 |-- try ----------->|                     |
 |                   |-- barrier TRIED     |
 |                   |-- 预占成功          |
 |-- report SUCCESS --------------------->|
 |                   |                  DB 未写入/响应丢失
 |-- rollback --------------------------->|
 |                   |<-- cancel ----------|
 |                   |-- barrier TRIED     |
 |                   |-- release           |
 |                   |-- CANCELLED ------->|
```

分析：

```text
TC branch 仍是 TRYING
不能进入 CONFIRMING
只能超时 CANCELLING
Cancel 通过 barrier 释放已成功预占
```

## 8. Confirm 部分失败/重试

```text
TM                  RM1                    RM2                    TC
 |                   |                      |                     |
 |-- commit ---------------------------------------------------->|
 |                   |<-- confirm ----------|                     |
 |                   |-- CONFIRMED          |                     |
 |                   |-- success ---------->|                     |
 |                   |                      |<-- confirm ---------|
 |                   |                      |-- 失败/超时         |
 |                   |                      |-- 保持 CONFIRMING   |
 |                   |                      |-- next_retry_at     |
 |                   |                      |                     |
 |                   |                      |<-- confirm 重试 ----|
 |                   |                      |-- CONFIRMED         |
 |<-------------------------------------------------- CONFIRMED ---|
```

分析：

```text
只能重试失败分支
RM1 已 CONFIRMED 不重复执行业务
RM2 barrier 幂等
global 在全部 CONFIRMED 前保持 CONFIRMING
```

## 9. Confirm 响应丢失

```text
TC -> RM Confirm
RM 本地事务已提交 CONFIRMED
响应丢失
TC 重试 Confirm
RM barrier 看到 CONFIRMED
-> 直接返回成功
```

分析：

```text
Confirm 必须幂等
Outbox 用稳定 eventKey
eventKey 唯一键防重复事件
```

## 10. Cancel 部分失败/重试

```text
TC -> RM1 cancel
RM1 -> CANCELLED
TC -> RM2 cancel
RM2 失败
TC 保持 CANCELLING
重试 RM2
RM2 -> CANCELLED
global -> CANCELLED
```

分析：

```text
已取消分支不重复释放
失败分支继续重试
超过上限 -> DEAD / global FAILED
```

## 11. Cancel 先到（空回滚）

```text
TC -> RM cancel
RM barrier 无记录
RM INSERT SUSPENDED
RM 返回成功
```

分析：

```text
不能释放不存在的资源
必须留下持久 SUSPENDED
```

## 12. Try 后到（悬挂）

```text
TC -> RM cancel
RM -> SUSPENDED
之后 Try 到达
RM 查 barrier = SUSPENDED
RM 拒绝 Try
```

分析：

```text
如果不拒绝，资源会被预占但没有事务管理
```

## 13. TC 崩溃

### TRYING 崩溃

```text
global TRYING
TM 已断开
timeout_at 到期
新 TC 抢 lease
-> CANCELLING
-> 调 Cancel
```

### CONFIRMING 崩溃

```text
global CONFIRMING
branch 状态已落库
新 TC 抢 lease
-> 扫描 CONFIRMING branch
-> 重试 Confirm
```

### CANCELLING 崩溃

```text
global CANCELLING
新 TC 继续 Cancel
```

分析：

```text
TC 无本地状态
全部依赖 t_tcc_global / t_tcc_branch
lease 过期后接管
```

## 14. RM 崩溃

### Try 前崩溃

```text
barrier 无记录
TC branch TRYING
超时 -> Cancel
Cancel -> SUSPENDED
```

### Try 本地事务后崩溃

```text
barrier TRIED
TC branch TRYING
超时 -> Cancel
Cancel -> CANCELLED
```

### Confirm 后崩溃

```text
barrier CONFIRMED
TC 未收到响应
重试 Confirm
-> 幂等成功
```

### Cancel 后崩溃

```text
barrier CANCELLED
重试 Cancel
-> 幂等成功
```

## 15. 全局超时

```text
TM begin
已注册部分 branch
TM 崩溃/未 commit
timeout_at 到期
TC -> CANCELLING
逐 branch Cancel
-> CANCELLED
```

## 16. 多 TC 回调路由

```text
tc-0 registerBranch:
  callbackEndpoint -> t_tcc_branch

tc-3 commit:
  SELECT callbackEndpoint FROM t_tcc_branch
  -> 调 RM
```

分析：

```text
callback endpoint 存 DB
不存 TC 内存
任意 TC 都能回调
不需要 sticky
```

## 17. 状态图

### 全局

```text
TRYING
  | all Try success + commit
  v
CONFIRMING
  | all branches CONFIRMED
  v
CONFIRMED

TRYING
  | any Try fail/timeout
  v
CANCELLING
  | all branches CANCELLED/SUSPENDED
  v
CANCELLED

CONFIRMING/CANCELLING
  | retry exhausted
  v
FAILED
```

### 分支

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

CANCELLING
  | no barrier
  v
SUSPENDED

CONFIRMING/CANCELLING
  | retry exhausted
  v
DEAD
```

### Barrier

```text
[TRIED] --Confirm--> [CONFIRMED]
   |
   +--Cancel--> [CANCELLED]

无记录 --Cancel--> [SUSPENDED]
[TRIED/CONFIRMED] --Cancel--> 拒绝
[SUSPENDED/CANCELLED] --Try--> 拒绝
```

## 18. 关键决策

```text
1. registerBranch 在 Try 前
2. branch_id 由 TC 的 branch_seq 生成
3. barrier 与业务、Outbox 同本地事务
4. Confirm/Cancel 失败不回退状态
5. DEAD -> global FAILED
6. owner_token/lease 只在 global
7. callbackEndpoint 落 branch
8. 任意 TC 都能按 endpoint 回调
9. 状态更新全部 CAS
```
