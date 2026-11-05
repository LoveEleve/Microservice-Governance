INSERT INTO lab_stock (sku_id, available, reserved, used)
VALUES ('sku-1', 100, 0, 0)
ON DUPLICATE KEY UPDATE available = VALUES(available);
