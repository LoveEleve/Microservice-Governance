# Seata TCC Multi-Service Lab

对应 Seata 小册第 4 章：两个服务、两个数据库、Seata TCC 和 fence。

## 结构

```text
order-service       :18083   订单库 seata_tcc_order
inventory-service   :18084   库存库 seata_tcc_inventory
Seata TC            :8091
```

```text
order-service       lab_order
inventory-service   lab_stock + tcc_fence_log
```

## 调用链

```text
POST /lab/seata/tcc/orders
  ↓
OrderService.createOrder()
  @GlobalTransactional
  ├── insert lab_order
  └── HTTP POST inventory-service /internal/tcc/inventory/try
        → InventoryTccAction.tryDeduct()
        → available - count, reserved + count
```

二阶段：

```text
全部成功：
  TC → inventory-service 的 TCC RM → confirm()
  → reserved - count, used + count

任一失败：
  TC → inventory-service 的 TCC RM → cancel()
  → reserved - count, available + count
```

XID 透传：

```text
order-service RestTemplate 拦截器
  → RootContext.getXID()
  → TX_XID 请求头

inventory-service SeataXidFilter
  → RootContext.bind(xid)
```

## 启动前

```text
1. 建两个库：
   seata_tcc_order
   seata_tcc_inventory
2. 启动 Seata Server，默认 127.0.0.1:8091
3. 修改两个 application.yml 的数据库账号密码
4. schema.sql / data.sql 会自动执行
```

启动顺序：

```text
inventory-service
order-service
```

## 请求示例

```bash
curl -X POST 'http://127.0.0.1:18083/lab/seata/tcc/orders?orderId=o-1&skuId=sku-1&userId=u-1&count=1'
```

失败路径：

```bash
curl -X POST 'http://127.0.0.1:18083/lab/seata/tcc/orders?orderId=o-2&skuId=sku-1&userId=u-1&count=99999'
```

Try 返回 false，订单事务回滚，库存不会留下预留。

## 观察端点

```text
GET http://127.0.0.1:18083/lab/seata/tcc/order/detail/{orderId}
GET http://127.0.0.1:18084/lab/seata/tcc/stock/{skuId}
GET http://127.0.0.1:18084/lab/seata/tcc/fence-log
```

## 说明

- TCC 使用普通 DataSource + 本地事务，不套 AT 的 DataSourceProxy。
- `useTCCFence=true` 需要 `tcc_fence_log` 表。
- 这是教学 lab，不是生产工程；没有鉴权、业务幂等表、对账和人工补偿台。
