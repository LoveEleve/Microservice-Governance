CREATE TABLE IF NOT EXISTS flight_booking (
    user_id VARCHAR(64) PRIMARY KEY,
    status VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS hotel_booking (
    user_id VARCHAR(64) PRIMARY KEY,
    status VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS car_booking (
    user_id VARCHAR(64) PRIMARY KEY,
    status VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS seata_state_machine_def (
    id VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(32) NOT NULL,
    app_name VARCHAR(32) NOT NULL,
    type VARCHAR(20),
    comment_ VARCHAR(255),
    ver VARCHAR(16) NOT NULL,
    gmt_create DATETIME(3) NOT NULL,
    status VARCHAR(2) NOT NULL,
    content TEXT,
    recover_strategy VARCHAR(16),
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS seata_state_machine_inst (
    id VARCHAR(128) NOT NULL,
    machine_id VARCHAR(32) NOT NULL,
    tenant_id VARCHAR(32) NOT NULL,
    parent_id VARCHAR(128),
    gmt_started DATETIME(3) NOT NULL,
    business_key VARCHAR(48),
    start_params TEXT,
    gmt_end DATETIME(3),
    excep BLOB,
    end_params TEXT,
    status VARCHAR(2),
    compensation_status VARCHAR(2),
    is_running TINYINT(1),
    gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY unikey_buz_tenant (business_key, tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS seata_state_inst (
    id VARCHAR(48) NOT NULL,
    machine_inst_id VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(20),
    service_name VARCHAR(128),
    service_method VARCHAR(128),
    service_type VARCHAR(16),
    business_key VARCHAR(48),
    state_id_compensated_for VARCHAR(50),
    state_id_retried_for VARCHAR(50),
    gmt_started DATETIME(3) NOT NULL,
    is_for_update TINYINT(1),
    input_params TEXT,
    output_params TEXT,
    status VARCHAR(2) NOT NULL,
    excep BLOB,
    gmt_updated DATETIME(3),
    gmt_end DATETIME(3),
    PRIMARY KEY (id, machine_inst_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
