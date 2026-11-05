# Seata AT Multi-Service Lab

对应 Seata 小册第 2 章：三个服务、三个数据库、AT 和 XID 透传。

## 结构

```text
order-service        :18080   订单库 seata_at_order
inventory-service    :18081   库存库 seata_at_inventory
account-service      :18082   账户库 seata_at_account
Seata TC             :8091
```

三个服务各自的数据库里都有 `undo_log` 表。

```text
order-service      → lab_order + undo_log
inventory-service  → lab_stock + undo_log
account-service    → lab_account + undo_log
```

## 调用链

```text
POST /lab/seata/orders
  ↓
OrderService.createOrder()
  @GlobalTransactional
  ├── insert lab_order（订单库本地事务）
  ├── POST inventory-service /internal/inventory/deduct
  └── POST account-service   /internal/account/deduct
```

XID 通过 `TX_XID` 请求头传递：

```text
OrderService RestTemplate 拦截器
  → RootContext.getXID()
  → 放到 TX_XID

Inventory/Account 的 SeataXidFilter
  → 从 TX_XID 读取
  → RootContext.bind(xid)
```

## 启动前

```text
1. 建三个库：
   seata_at_order
   seata_at_inventory
   seata_at_account
2. 启动 Seata Server，默认 127.0.0.1:8091
3. 修改三个 application.yml 的数据库账号密码
4. schema.sql / data.sql 会自动执行
```

启动顺序：

```text
inventory-service
account-service
order-service
```

## 请求示例

成功：

```bash
curl -X POST 'http://127.0.0.1:18080/lab/seata/orders?orderId=o-1&skuId=sku-1&userId=u-1&amount=100'
```

失败：

```bash
curl -X POST 'http://127.0.0.1:18080/lab/seata/orders?orderId=o-2&skuId=sku-1&userId=u-1&amount=99999'
```

账户余额不足，订单和库存应被 AT 回滚。

## 观察端点

```text
GET http://127.0.0.1:18080/lab/seata/order/detail/{orderId}
GET http://127.0.0.1:18080/lab/seata/order/undo-log
GET http://127.0.0.1:18081/lab/seata/inventory/stock/{skuId}
GET http://127.0.0.1:18081/lab/seata/inventory/undo-log
GET http://127.0.0.1:18082/lab/seata/account/balance/{userId}
GET http://127.0.0.1:18082/lab/seata/account/undo-log
```

`xid` 端点只对当前请求线程有意义：

```text
GET /lab/seata/order/xid
GET /lab/seata/inventory/xid
GET /lab/seata/account/xid
```

## 说明

- 这是教学 lab，不是生产工程；没有鉴权、幂等表、对账、重试治理。
- 需要 3 个 MySQL 库 + Seata Server，不默认启动。
- 三个服务各自有 DataSourceProxy 和 undo_log；这是与单进程示例最大的区别。
