CREATE TABLE IF NOT EXISTS t_consume_record (
    msg_id VARCHAR(64) PRIMARY KEY,
    event_key VARCHAR(128),
    biz_code VARCHAR(32) NOT NULL,
    biz_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    stream_entry_id VARCHAR(64) NOT NULL,
    consumer_id VARCHAR(64),
    status VARCHAR(16) NOT NULL,
    owner_token VARCHAR(64),
    owner_epoch BIGINT NOT NULL DEFAULT 0,
    claim_count INT NOT NULL DEFAULT 0,
    lease_until DATETIME,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME,
    payload MEDIUMTEXT,
    payload_ref VARCHAR(256),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_status_lease (status, lease_until),
    KEY idx_biz_claim (biz_code, status, next_retry_at, created_at),
    KEY idx_owner_token (owner_token, status),
    KEY idx_stream_entry (stream_entry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_idempotent (
    msg_id VARCHAR(64) PRIMARY KEY,
    biz_key VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_biz_key (biz_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
