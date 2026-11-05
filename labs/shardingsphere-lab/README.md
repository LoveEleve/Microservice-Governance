# ShardingSphere Lab

对应 ShardingSphere book 01-08 的 JDBC 分片主链。

> 已在临时 MySQL 8.0 上验证：4 分片路由、binding table、broadcast table、Snowflake 主键。

## 拓扑

```text
ds_0 ----------------- ds_1
  t_order_0              t_order_0
  t_order_1              t_order_1
  t_order_item_0         t_order_item_0
  t_order_item_1         t_order_item_1
  t_dict                 t_dict
```

- 2 个数据源 × 2 张表 = 4 个 `t_order` 物理分片
- `user_id` 同时做库分片和表分片
- `t_order` / `t_order_item` 是 binding table（同一 `user_id` 落同一分片）
- `t_dict` 是 broadcast table（两个库各一份；写入广播，查询通常取一份）
- `order_id` / `item_id` 由 ShardingSphere `SNOWFLAKE` 生成

## 展示什么

```text
POST /lab/sharding/orders?userId=1&amount=99&product=book&quantity=1
  → 生成 order_id，写 t_order + t_order_item
  → 同 user_id 落同一分片

GET  /lab/sharding/orders?userId=1
  → 带分片键，单分片

GET  /lab/sharding/orders/{orderId}
  → 不带分片键，广播到所有分片

GET  /lab/sharding/items?userId=1
  → binding table 查询

POST /lab/sharding/dict?type=order_status&value=NEW
  → broadcast table 写入

GET  /lab/sharding/dict?type=order_status
  → broadcast table 查询
```

## 依赖

```text
JDK 17
Spring Boot 3.2.5
ShardingSphere-JDBC 5.5.2
MySQL 8.0.33
```

不要求默认启动。在 `labs/` 根目录执行：

```bash
docker compose -f shardingsphere-lab/docker-compose.yml up -d
```

初始化脚本会自动创建 `sharding_lab_ds0`、`sharding_lab_ds1` 和全部分片表。

```bash
mvn -pl shardingsphere-lab -am package
java -jar shardingsphere-lab/target/shardingsphere-lab-1.0.0-SNAPSHOT.jar
```

## 配置

`src/main/resources/shardingsphere.yaml`：

```text
ds_0 / ds_1
  → HikariDataSource + MySQL

!SHARDING
  → t_order / t_order_item 按 user_id 分库分表
  → bindingTables: t_order,t_order_item
  → keyGenerators: SNOWFLAKE

!BROADCAST
  → t_dict
```

`application.yml` 只做一件事：让 Spring Boot 用 ShardingSphere JDBC 驱动。

```yaml
spring:
  datasource:
    driver-class-name: org.apache.shardingsphere.driver.ShardingSphereDriver
    url: jdbc:shardingsphere:classpath:shardingsphere.yaml
```

## 观察 SQL

`sql-show: true` 会打印逻辑 SQL 改写后的真实 SQL 和数据源，便于对照：

```text
逻辑：select ... from t_order where user_id = ?
真实：select ... from t_order_1 where user_id = ?
```

## 边界

- 教学 lab，不是生产工程；没有分片扩容、读写分离、加密、影子库。
- `@Transactional` 默认走 LOCAL：order + item 同分片时是本地事务；`t_dict` 广播写两个库时不是跨库原子，生产要评估 XA/BASE。
- 两个 MySQL schema 在同一个 MySQL 实例中，方便本地演示；生产通常是多个实例。
- 不要求实际启动 MySQL；配置和 schema 按真实 MySQL 8 写。
- `GET /orders/{orderId}` 故意不带 `user_id`，用来展示广播；生产应尽量补分片键。
