INSERT IGNORE INTO users (id, name, age)
VALUES (1, 'Alice', 20), (2, 'Bob', 30), (3, 'Carol', 40);

INSERT IGNORE INTO orders (id, order_no, user_id, amount)
VALUES (101, 'ORD-101', 1, 99.00), (102, 'ORD-102', 1, 199.00);

INSERT IGNORE INTO order_item (id, order_id, product_name, quantity)
VALUES (1001, 101, 'book', 1), (1002, 102, 'pen', 2);
