CREATE TABLE IF NOT EXISTS id_segment (
  biz_type VARCHAR(64) PRIMARY KEY COMMENT '业务 key，例如 tcc-xid',
  max_id   BIGINT NOT NULL COMMENT '已分配出去的最大 ID',
  step     INT NOT NULL COMMENT '每次领取数量，也是自适应步长下限'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO id_segment (biz_type, max_id, step)
VALUES ('tcc-xid', 0, 1000)
ON DUPLICATE KEY UPDATE biz_type = biz_type;
