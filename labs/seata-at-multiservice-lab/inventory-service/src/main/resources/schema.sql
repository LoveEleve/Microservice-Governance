CREATE TABLE IF NOT EXISTS lab_stock (
    sku_id VARCHAR(64) PRIMARY KEY,
    quantity INT NOT NULL
);

CREATE TABLE IF NOT EXISTS undo_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    branch_id BIGINT NOT NULL,
    xid VARCHAR(128) NOT NULL,
    context VARCHAR(128) NOT NULL,
    rollback_info LONGBLOB NOT NULL,
    log_status INT NOT NULL,
    log_created DATETIME NOT NULL,
    log_modified DATETIME NOT NULL,
    ext VARCHAR(100) DEFAULT NULL,
    UNIQUE KEY ux_undo_log (xid, branch_id)
);
