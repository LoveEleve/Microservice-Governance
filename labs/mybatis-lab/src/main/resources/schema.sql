CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    age INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_orders_order_no (order_no),
    KEY idx_orders_user_id (user_id)
);

CREATE TABLE IF NOT EXISTS order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    quantity INT NOT NULL,
    KEY idx_order_item_order_id (order_id)
);
