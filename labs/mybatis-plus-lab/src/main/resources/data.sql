INSERT IGNORE INTO users (id, name, age, deleted)
VALUES (1, 'Alice', 20, 0),
       (2, 'Bob', 30, 0),
       (3, 'Carol', 40, 0),
       (4, 'Deleted', 50, 1);
