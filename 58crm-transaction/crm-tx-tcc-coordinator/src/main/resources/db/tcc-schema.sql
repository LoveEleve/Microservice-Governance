CREATE TABLE IF NOT EXISTS t_tcc_global (
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

CREATE TABLE IF NOT EXISTS t_tcc_branch (
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
