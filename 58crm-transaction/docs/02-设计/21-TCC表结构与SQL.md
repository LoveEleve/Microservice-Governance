# TCC 表结构与 SQL

> 读完你能做什么：知道 TCC 三张表的最终字段、约束、关键状态 SQL 和 owner lease 规则。

## 1. t_tcc_global

```sql
CREATE TABLE t_tcc_global (
  xid           BIGINT       NOT NULL,
  request_id    VARCHAR(128) NOT NULL,
  biz_code      VARCHAR(64)  NOT NULL,
  biz_id        VARCHAR(128) NOT NULL,
  status        VARCHAR(16)  NOT NULL,
  timeout_at    DATETIME(3)  NOT NULL,
  branch_seq    BIGINT       NOT NULL DEFAULT 0,
  owner_token   VARCHAR(64)  NULL,
  lease_until   DATETIME(3)  NULL,
  created_at    DATETIME(3)  NOT NULL,
  updated_at    DATETIME(3)  NOT NULL,
  PRIMARY KEY (xid),
  UNIQUE KEY uk_request_id (request_id),
  KEY idx_status_timeout (status, timeout_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
```

字段：

```text
xid           TCC 号段生成
request_id    begin 幂等键
biz_code      业务域
biz_id        业务单号
status        TRYING/CONFIRMING/CONFIRMED/CANCELLING/CANCELLED/FAILED
timeout_at    TRYING 超时点
branch_seq    registerBranch 原子递增
owner_token   当前 owner
lease_until   owner 租约到期
```

## 2. t_tcc_branch

```sql
CREATE TABLE t_tcc_branch (
  xid               BIGINT       NOT NULL,
  branch_id         BIGINT       NOT NULL,
  resource_key      VARCHAR(128) NOT NULL,
  action_name       VARCHAR(128) NOT NULL,
  callback_endpoint VARCHAR(256) NOT NULL,
  status            VARCHAR(16)  NOT NULL,
  retry_count       INT          NOT NULL DEFAULT 0,
  next_retry_at     DATETIME(3)  NULL,
  application_data  MEDIUMTEXT   NULL,
  created_at        DATETIME(3)  NOT NULL,
  updated_at        DATETIME(3)  NOT NULL,
  PRIMARY KEY (xid, branch_id),
  UNIQUE KEY uk_xid_resource (xid, resource_key),
  KEY idx_status_retry (status, next_retry_at),
  KEY idx_xid_status (xid, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
```

字段：

```text
xid/branch_id       分支主键
resource_key        分支幂等键
action_name         TCC 动作名
callback_endpoint   RM 回调地址
status              TRYING/TRY_SUCCESS/TRY_FAIL/CONFIRMING/CONFIRMED/CANCELLING/CANCELLED/SUSPENDED/DEAD
retry_count         Confirm/Cancel 重试次数
next_retry_at       分支退避时间
application_data    Confirm/Cancel 参数快照，<=64KB
```

## 3. t_branch_barrier

```sql
CREATE TABLE t_branch_barrier (
  xid          BIGINT       NOT NULL,
  branch_id    BIGINT       NOT NULL,
  action_name  VARCHAR(128) NOT NULL,
  status       VARCHAR(16)  NOT NULL,
  created_at   DATETIME(3)  NOT NULL,
  updated_at   DATETIME(3)  NOT NULL,
  PRIMARY KEY (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
```

barrier 状态：

```text
TRIED
CONFIRMED
CANCELLED
SUSPENDED
```

## 4. Begin

```sql
INSERT INTO t_tcc_global
(xid, request_id, biz_code, biz_id, status, timeout_at,
 branch_seq, owner_token, lease_until, created_at, updated_at)
VALUES
(?, ?, ?, ?, 'TRYING', DATE_ADD(NOW(3), INTERVAL ? SECOND),
 0, NULL, NULL, NOW(3), NOW(3));
```

`request_id` 冲突：

```text
SELECT xid, status FROM t_tcc_global WHERE request_id = ?;
返回已有 xid
```

## 5. RegisterBranch

```sql
START TRANSACTION;

UPDATE t_tcc_global
SET branch_seq = branch_seq + 1,
    updated_at = NOW(3)
WHERE xid = ?
  AND status = 'TRYING';

SELECT branch_seq
FROM t_tcc_global
WHERE xid = ?;

INSERT INTO t_tcc_branch
(xid, branch_id, resource_key, action_name, callback_endpoint,
 status, retry_count, next_retry_at, application_data, created_at, updated_at)
VALUES
(?, ?, ?, ?, ?, 'TRYING', 0, NULL, ?, NOW(3), NOW(3));

COMMIT;
```

`UNIQUE(xid, resource_key)` 冲突：

```text
SELECT branch_id, status FROM t_tcc_branch WHERE xid = ? AND resource_key = ?;
返回已有 branch
```

允许 branch_seq 出现空洞，不能回退。

## 6. ReportBranch

```sql
UPDATE t_tcc_branch b
JOIN t_tcc_global g ON g.xid = b.xid
SET b.status = ?,
    b.updated_at = NOW(3)
WHERE b.xid = ?
  AND b.branch_id = ?
  AND b.status = 'TRYING'
  AND g.status = 'TRYING';
```

只允许：

```text
TRY_SUCCESS
TRY_FAIL
```

重复上报：

```text
已是 TRY_SUCCESS/TRY_FAIL -> 幂等返回
已进入 CONFIRMING/CANCELLING -> TCC_INVALID_STATE
```

这里不加 owner 条件：Try 上报不推进二阶段，只允许在 global TRYING 时落分支结果。

## 7. Commit

```sql
START TRANSACTION;

SELECT status, owner_token, lease_until
FROM t_tcc_global
WHERE xid = ?
FOR UPDATE;

SELECT status
FROM t_tcc_branch
WHERE xid = ?
FOR UPDATE;

-- 全部 TRY_SUCCESS
UPDATE t_tcc_global
SET status = 'CONFIRMING',
    owner_token = ?,
    lease_until = DATE_ADD(NOW(3), INTERVAL ? SECOND),
    updated_at = NOW(3)
WHERE xid = ?
  AND status = 'TRYING';

COMMIT;
```

判定：

```text
有 TRYING       -> TCC_NOT_READY
有 TRY_FAIL     -> TCC_INVALID_STATE
全部 TRY_SUCCESS -> CONFIRMING
```

## 8. Rollback

```sql
START TRANSACTION;

SELECT status, owner_token, lease_until
FROM t_tcc_global
WHERE xid = ?
FOR UPDATE;

UPDATE t_tcc_global
SET status = 'CANCELLING',
    owner_token = ?,
    lease_until = DATE_ADD(NOW(3), INTERVAL ? SECOND),
    updated_at = NOW(3)
WHERE xid = ?
  AND status = 'TRYING';

COMMIT;
```

判定：

```text
TRYING      -> CANCELLING
CANCELLING  -> 幂等返回
CANCELLED   -> 幂等返回
CONFIRMING  -> TCC_INVALID_STATE
CONFIRMED   -> TCC_INVALID_STATE
FAILED      -> 返回 FAILED
```

## 9. owner lease

```sql
UPDATE t_tcc_global
SET owner_token = ?,
    lease_until = DATE_ADD(NOW(3), INTERVAL ? SECOND),
    updated_at = NOW(3)
WHERE xid = ?
  AND status IN ('TRYING','CONFIRMING','CANCELLING')
  AND (owner_token IS NULL OR lease_until < NOW(3));
```

续约：

```sql
UPDATE t_tcc_global
SET lease_until = DATE_ADD(NOW(3), INTERVAL ? SECOND),
    updated_at = NOW(3)
WHERE xid = ?
  AND owner_token = ?
  AND lease_until > NOW(3);
```

影响 0 行：

```text
owner 已丢失
当前线程停止推进
```

## 10. branch 推进

Confirm 抢占：

```sql
UPDATE t_tcc_branch b
JOIN t_tcc_global g ON g.xid = b.xid
SET b.status = 'CONFIRMING',
    b.updated_at = NOW(3)
WHERE b.xid = ?
  AND b.branch_id = ?
  AND b.status = 'TRY_SUCCESS'
  AND g.status = 'CONFIRMING'
  AND g.owner_token = ?
  AND g.lease_until > NOW(3);
```

Confirm 成功：

```sql
UPDATE t_tcc_branch b
JOIN t_tcc_global g ON g.xid = b.xid
SET b.status = 'CONFIRMED',
    b.updated_at = NOW(3)
WHERE b.xid = ?
  AND b.branch_id = ?
  AND b.status = 'CONFIRMING'
  AND g.owner_token = ?
  AND g.lease_until > NOW(3);
```

Cancel 抢占：

```sql
UPDATE t_tcc_branch b
JOIN t_tcc_global g ON g.xid = b.xid
SET b.status = 'CANCELLING',
    b.updated_at = NOW(3)
WHERE b.xid = ?
  AND b.branch_id = ?
  AND b.status IN ('TRYING','TRY_SUCCESS','TRY_FAIL')
  AND g.status = 'CANCELLING'
  AND g.owner_token = ?
  AND g.lease_until > NOW(3);
```

## 11. 失败重试

```sql
UPDATE t_tcc_branch
SET retry_count = retry_count + 1,
    next_retry_at = ?,
    updated_at = NOW(3)
WHERE xid = ?
  AND branch_id = ?
  AND status IN ('CONFIRMING','CANCELLING');
```

退避：

```text
1s / 5s / 30s / 2min / 10min
```

达到上限：

```sql
UPDATE t_tcc_branch
SET status = 'DEAD',
    updated_at = NOW(3)
WHERE xid = ? AND branch_id = ? AND status = 'CONFIRMING';

UPDATE t_tcc_global
SET status = 'FAILED',
    updated_at = NOW(3)
WHERE xid = ? AND status = 'CONFIRMING';
```

## 12. Global 收敛

Confirm 完成：

```sql
SELECT COUNT(*) AS total,
       SUM(status = 'CONFIRMED') AS confirmed
FROM t_tcc_branch
WHERE xid = ?;
```

```text
confirmed = total -> global CONFIRMED
```

Cancel 完成：

```text
所有 branch 都在 CANCELLED/SUSPENDED
  -> global CANCELLED
```

DEAD：

```text
任意 branch DEAD
  -> global FAILED
  -> ERROR 日志
```

## 13. Scheduler 扫描

二阶段到期：

```sql
SELECT xid
FROM t_tcc_branch
WHERE status IN ('CONFIRMING','CANCELLING')
  AND next_retry_at <= NOW(3)
GROUP BY xid
LIMIT ?;
```

TRYING 超时：

```sql
SELECT xid
FROM t_tcc_global
WHERE status = 'TRYING'
  AND timeout_at <= NOW(3)
LIMIT ?;
```

## 14. 不加的东西

```text
外键
callback_token
mTLS
allowlist
版本号 version
global.retry_count
global.next_retry_at
```

## 15. 待确认

```text
1. applicationData 上限是否固定 64KB
2. timeout_at 是否只用于 TRYING
3. retry_count 上限按分支还是按 global
4. DEAD 是否允许人工改状态
5. TRYING 分支 Cancel 是否一律先 CANCELLING
```

## 16. 一句话

> **global 管 owner 和总状态，branch 管分支进度和重试，barrier 管业务幂等；所有二阶段写入都必须带 owner fencing。**
