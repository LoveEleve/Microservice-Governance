CREATE DATABASE IF NOT EXISTS crm_tx_e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE crm_tx_e2e;

DROP TABLE IF EXISTS t_tcc_global;
DROP TABLE IF EXISTS t_tcc_branch;
DROP TABLE IF EXISTS id_segment;
DROP TABLE IF EXISTS t_branch_barrier;
DROP TABLE IF EXISTS t_local_message;
DROP TABLE IF EXISTS t_quota;
DROP TABLE IF EXISTS t_lead;

CREATE TABLE id_segment (
  biz_type VARCHAR(64) PRIMARY KEY,
  max_id BIGINT NOT NULL,
  step INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
INSERT INTO id_segment (biz_type, max_id, step) VALUES ('tcc-xid', 0, 1000);

CREATE TABLE t_tcc_global (
  xid BIGINT NOT NULL,
  request_id VARCHAR(128) NOT NULL,
  biz_code VARCHAR(64) NOT NULL,
  biz_id VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL,
  timeout_at DATETIME(3) NOT NULL,
  branch_seq BIGINT NOT NULL DEFAULT 0,
  owner_token VARCHAR(64) NULL,
  lease_until DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (xid),
  UNIQUE KEY uk_request_id (request_id),
  KEY idx_status_timeout (status, timeout_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE t_tcc_branch (
  xid BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  resource_key VARCHAR(128) NOT NULL,
  action_name VARCHAR(128) NOT NULL,
  callback_endpoint VARCHAR(256) NOT NULL,
  status VARCHAR(16) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NULL,
  application_data MEDIUMTEXT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (xid, branch_id),
  UNIQUE KEY uk_xid_resource (xid, resource_key),
  KEY idx_status_retry (status, next_retry_at),
  KEY idx_xid_status (xid, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE t_branch_barrier (
  xid BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  action_name VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE t_local_message (
  msg_id VARCHAR(64) PRIMARY KEY,
  event_key VARCHAR(128) NOT NULL UNIQUE,
  biz_code VARCHAR(32) NOT NULL,
  biz_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  payload MEDIUMTEXT,
  payload_ref VARCHAR(256),
  status VARCHAR(16) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3),
  stream_entry_id VARCHAR(64),
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE t_quota (
  user_id BIGINT PRIMARY KEY,
  reserved INT NOT NULL DEFAULT 0,
  assigned INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
INSERT INTO t_quota (user_id, reserved, assigned) VALUES (1, 0, 0);

CREATE TABLE t_lead (
  id BIGINT PRIMARY KEY,
  status VARCHAR(16) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
INSERT INTO t_lead (id, status) VALUES (1001, 'NEW');
