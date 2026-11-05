CREATE TABLE IF NOT EXISTS lab_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id VARCHAR(64) NOT NULL,
    sku_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    count INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_id (order_id)
);

CREATE TABLE IF NOT EXISTS lab_stock (
    sku_id VARCHAR(64) PRIMARY KEY,
    available INT NOT NULL,
    reserved INT NOT NULL DEFAULT 0,
    used INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tcc_fence_log (
    xid VARCHAR(128) NOT NULL,
    branch_id BIGINT NOT NULL,
    action_name VARCHAR(64) NOT NULL,
    status TINYINT NOT NULL,
    gmt_create DATETIME(3) NOT NULL,
    gmt_modified DATETIME(3) NOT NULL,
    PRIMARY KEY (xid, branch_id),
    KEY idx_gmt_modified (gmt_modified),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
