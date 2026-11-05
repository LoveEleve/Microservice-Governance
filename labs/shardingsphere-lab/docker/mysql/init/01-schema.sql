CREATE DATABASE IF NOT EXISTS sharding_lab_ds0 DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS sharding_lab_ds1 DEFAULT CHARACTER SET utf8mb4;

USE sharding_lab_ds0;
CREATE TABLE IF NOT EXISTS t_order_0 (
    order_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_order_user (user_id)
);
CREATE TABLE IF NOT EXISTS t_order_1 LIKE t_order_0;
CREATE TABLE IF NOT EXISTS t_order_item_0 (
    item_id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    quantity INT NOT NULL,
    KEY idx_item_order (order_id),
    KEY idx_item_user (user_id)
);
CREATE TABLE IF NOT EXISTS t_order_item_1 LIKE t_order_item_0;
CREATE TABLE IF NOT EXISTS t_dict (
    dict_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dict_type VARCHAR(64) NOT NULL,
    dict_value VARCHAR(256) NOT NULL,
    UNIQUE KEY uk_dict_type (dict_type)
);

USE sharding_lab_ds1;
CREATE TABLE IF NOT EXISTS t_order_0 LIKE sharding_lab_ds0.t_order_0;
CREATE TABLE IF NOT EXISTS t_order_1 LIKE sharding_lab_ds0.t_order_0;
CREATE TABLE IF NOT EXISTS t_order_item_0 LIKE sharding_lab_ds0.t_order_item_0;
CREATE TABLE IF NOT EXISTS t_order_item_1 LIKE sharding_lab_ds0.t_order_item_0;
CREATE TABLE IF NOT EXISTS t_dict LIKE sharding_lab_ds0.t_dict;
