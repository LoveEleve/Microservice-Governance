# Seata Overview Lab

对应 Seata 小册第 1 章：TC / TM / RM、XID、AT 的基本接线。

## 这个 lab 展示什么

```text
OrderService.createOrder()  @GlobalTransactional
  ├── 本地写 lab_order
  ├── 调 InventoryService.deduct() 更新 lab_stock
  └── 调 AccountService.deduct() 更新 lab_account
```

- `OrderService`：既是 TM，也是 RM（订单库分支）
- `InventoryService` / `AccountService`：RM
- `DataSourceProxy`：把 JDBC 连接交给 Seata，AT 才能记录 undo_log、申请全局锁
- `@GlobalTransactional`：全局事务入口
- `schema.sql`：业务表 + `undo_log`

## 依赖

```text
JDK 17
MySQL 8
Seata Server 2.1.2（或 2.0.x）
```

启动前需要：

```text
1. 建库 seata_overview_lab
2. 执行 src/main/resources/schema.sql 和 data.sql
3. 启动 Seata Server，默认 127.0.0.1:8091
4. 修改 application.yml 的数据库账号密码
```

## 请求示例

```bash
curl -X POST 'http://127.0.0.1:18080/lab/seata/orders?orderId=o-1&skuId=sku-1&userId=u-1&amount=100'
```

失败路径：把 `amount` 调到账户余额不足，`AccountService` 抛异常，观察订单和库存是否被 Seata 回滚。

## AT 观察端点

```text
GET /lab/seata/at/xid              当前线程的 XID
GET /lab/seata/at/stock/{skuId}    库存
GET /lab/seata/at/account/{userId} 账户余额
GET /lab/seata/at/undo-log         最近 20 条 undo_log
```

这些端点用于对照第 2 章：一阶段提交后 `undo_log` 是否存在、二阶段提交后是否被删除、回滚后业务表是否恢复。

## 配置文件

`src/main/resources/` 下包含 `registry.conf` 和 `file.conf`，与 `application.yml` 一起作为 Seata 客户端配置；不要只改其中一个。

## 说明

- 这是教学 lab，不是生产工程；没有鉴权、幂等、对账、重试治理。
- 不要求默认启动；需要真实 MySQL 和 Seata Server 才能跑。
- 生产项目的消息队列用 Kafka/RocketMQ，不用 Redis List 冒充 MQ。
