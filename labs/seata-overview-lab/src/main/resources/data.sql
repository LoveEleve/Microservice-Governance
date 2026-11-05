INSERT INTO lab_stock (sku_id, quantity)
VALUES ('sku-1', 100)
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity);

INSERT INTO lab_account (user_id, balance)
VALUES ('u-1', 1000)
ON DUPLICATE KEY UPDATE balance = VALUES(balance);
