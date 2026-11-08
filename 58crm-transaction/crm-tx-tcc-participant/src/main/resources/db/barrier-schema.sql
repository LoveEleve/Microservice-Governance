CREATE TABLE IF NOT EXISTS t_branch_barrier (
  xid          BIGINT       NOT NULL,
  branch_id    BIGINT       NOT NULL,
  action_name  VARCHAR(128) NOT NULL,
  status       VARCHAR(16)  NOT NULL,
  created_at   DATETIME(3)  NOT NULL,
  updated_at   DATETIME(3)  NOT NULL,
  PRIMARY KEY (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
