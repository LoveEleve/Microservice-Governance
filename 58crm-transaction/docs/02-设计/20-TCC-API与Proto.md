# TCC API 与 Proto

> 读完你能做什么：知道 TM/TC/RM 各自的 RPC 边界、请求响应字段、幂等语义和错误码。

## 1. 边界

```text
TM -> TC：控制面 unary
TC -> RM：callback unary
TM -> RM：Try 是业务 API（本地方法或业务 RPC）
TC：不提供 Try RPC，只提供 Confirm/Cancel callback
```

```text
CoordinatorService
  Begin
  RegisterBranch
  ReportBranch
  Commit
  Rollback
  Query

ParticipantCallbackService
  Confirm
  Cancel
```

## 2. proto

```proto
syntax = "proto3";

package crm.tx.tcc.v1;

option java_package = "com.wuba.crm.tx.tcc.api.grpc";
option java_multiple_files = true;

enum GlobalStatus {
  GLOBAL_STATUS_UNSPECIFIED = 0;
  TRYING = 1;
  CONFIRMING = 2;
  CONFIRMED = 3;
  CANCELLING = 4;
  CANCELLED = 5;
  FAILED = 6;
}

enum BranchStatus {
  BRANCH_STATUS_UNSPECIFIED = 0;
  BRANCH_TRYING = 1;
  BRANCH_TRY_SUCCESS = 2;
  BRANCH_TRY_FAIL = 3;
  BRANCH_CONFIRMING = 4;
  BRANCH_CONFIRMED = 5;
  BRANCH_CANCELLING = 6;
  BRANCH_CANCELLED = 7;
  BRANCH_SUSPENDED = 8;
  BRANCH_DEAD = 9;
}

enum ResultCode {
  RESULT_CODE_UNSPECIFIED = 0;
  OK = 1;
  TCC_INVALID_ARGUMENT = 2;
  TCC_NOT_READY = 3;
  TCC_INVALID_STATE = 4;
  TCC_BUSY = 5;
  TCC_NOT_FOUND = 6;
  TCC_DUPLICATE = 7;
  TCC_RETRY_EXHAUSTED = 8;
  TCC_CALLBACK_FAILED = 9;
  TCC_INTERNAL_ERROR = 10;
}

message BeginRequest {
  string request_id = 1;
  string biz_code = 2;
  string biz_id = 3;
  int32 try_timeout_seconds = 4;
}

message BeginResponse {
  ResultCode code = 1;
  string message = 2;
  int64 xid = 3;
  GlobalStatus status = 4;
}

message RegisterBranchRequest {
  int64 xid = 1;
  string resource_key = 2;
  string action_name = 3;
  string callback_endpoint = 4;
  string application_data = 5;
}

message RegisterBranchResponse {
  ResultCode code = 1;
  string message = 2;
  int64 branch_id = 3;
  BranchStatus status = 4;
}

message ReportBranchRequest {
  int64 xid = 1;
  int64 branch_id = 2;
  BranchStatus status = 3;
  string message = 4;
}

message ReportBranchResponse {
  ResultCode code = 1;
  string message = 2;
}

message CommitRequest {
  int64 xid = 1;
}

message CommitResponse {
  ResultCode code = 1;
  string message = 2;
  GlobalStatus status = 3;
}

message RollbackRequest {
  int64 xid = 1;
}

message RollbackResponse {
  ResultCode code = 1;
  string message = 2;
  GlobalStatus status = 3;
}

message QueryRequest {
  int64 xid = 1;
}

message BranchInfo {
  int64 branch_id = 1;
  string resource_key = 2;
  string action_name = 3;
  BranchStatus status = 4;
  int32 retry_count = 5;
}

message QueryResponse {
  ResultCode code = 1;
  string message = 2;
  GlobalStatus status = 3;
  repeated BranchInfo branches = 4;
}

service TccCoordinator {
  rpc Begin(BeginRequest) returns (BeginResponse);
  rpc RegisterBranch(RegisterBranchRequest) returns (RegisterBranchResponse);
  rpc ReportBranch(ReportBranchRequest) returns (ReportBranchResponse);
  rpc Commit(CommitRequest) returns (CommitResponse);
  rpc Rollback(RollbackRequest) returns (RollbackResponse);
  rpc Query(QueryRequest) returns (QueryResponse);
}

enum PhaseResult {
  PHASE_RESULT_UNSPECIFIED = 0;
  PHASE_SUCCESS = 1;
  PHASE_RETRYABLE = 2;
  PHASE_PERMANENT_FAILURE = 3;
}

message ConfirmRequest {
  int64 xid = 1;
  int64 branch_id = 2;
  string action_name = 3;
  string application_data = 4;
}

message ConfirmResponse {
  PhaseResult result = 1;
  string message = 2;
}

message CancelRequest {
  int64 xid = 1;
  int64 branch_id = 2;
  string action_name = 3;
  string application_data = 4;
}

message CancelResponse {
  PhaseResult result = 1;
  string message = 2;
}

service TccParticipantCallback {
  rpc Confirm(ConfirmRequest) returns (ConfirmResponse);
  rpc Cancel(CancelRequest) returns (CancelResponse);
}
```

## 3. 字段说明

Begin：

```text
request_id           begin 幂等键
biz_code / biz_id    业务定位
try_timeout_seconds  TRYING 超时
```

RegisterBranch：

```text
xid                  全局事务
resource_key         分支幂等键
action_name          TCC 动作
callback_endpoint    RM 回调地址
application_data     Confirm/Cancel 上下文
```

ReportBranch：

```text
只允许 TRY_SUCCESS / TRY_FAIL
其他状态拒绝
```

Commit/Rollback：

```text
只传 xid
TC 自己检查 branch 状态
```

Callback：

```text
Confirm/Cancel 都带 xid + branchId + applicationData
返回 SUCCESS / RETRYABLE / PERMANENT_FAILURE
```

## 4. 幂等规则

```text
Begin(request_id)
  -> 重复请求返回同一 xid

RegisterBranch(xid, resource_key)
  -> 重复请求返回同一 branch_id

ReportBranch
  -> 重复 TRY_SUCCESS/TRY_FAIL 幂等
  -> 冲突状态返回 TCC_INVALID_STATE

Commit
  -> 已 CONFIRMED 返回 CONFIRMED
  -> CONFIRMING 返回当前状态
  -> TRY_FAIL/TRYING 按规则处理

Rollback
  -> 已 CANCELLED 返回 CANCELLED
  -> CONFIRMED 拒绝

Confirm/Cancel
  -> barrier 幂等
```

## 5. 错误码语义

```text
OK
TCC_INVALID_ARGUMENT
TCC_NOT_READY
  还有 branch TRYING，不能 commit

TCC_INVALID_STATE
  状态不允许当前操作，不可重试

TCC_BUSY
  owner lease 被占用，可短退避重试

TCC_NOT_FOUND
  xid/branch 不存在

TCC_DUPLICATE
  重复请求；通常可直接当成功

TCC_RETRY_EXHAUSTED
  branch/global 已 DEAD/FAILED

TCC_CALLBACK_FAILED
  RM 回调失败，等待重试

TCC_INTERNAL_ERROR
  服务端未预期异常
```

## 6. 超时

```text
TM -> TC：deadline > try_timeout
TC -> RM：deadline 短于 branch lease 剩余时间
```

不在 proto 里传 deadline；由 gRPC deadline 控制。

## 7. 明确不做

```text
callback 鉴权
token
mTLS
allowlist
管理台
告警平台
Try RPC（Try 由业务 API 发起）
```

## 8. 已确认

```text
application_data 用 string，UTF-8，<=64KB
Query 不返回 callbackEndpoint
Commit/Rollback 不带 request_id，xid 本身幂等
ReportBranch 用 message 即可，不单独 error_code
PERMANENT_FAILURE 只给明确不可重试的业务失败
网络超时/连接失败/RM 暂时不可用 -> RETRYABLE
```

Query 不返回 callbackEndpoint 的原因：

```text
callbackEndpoint 是 TC 内部路由数据
不属于业务查询 DTO
```

## 9. 一句话

> **TM 通过 TccCoordinator 控制事务；RM 通过 TccParticipantCallback 接收 Confirm/Cancel；Try 仍然是业务自己的 API，不进入 TC 的 RPC。**
