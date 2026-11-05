# Seata TCC Lab

对应 Seata 小册第 4 章：Seata TCC 接口、`BusinessActionContext` 和 fence 表。

## 这个 lab 展示什么

```text
InventoryTccAction
  @LocalTCC
  @TwoPhaseBusinessAction(useTCCFence = true)
  tryDeduct / confirm / cancel

OrderService.createOrder()
  @GlobalTransactional
  → 写 lab_order
  → 调 InventoryTccAction.tryDeduct()
  → 失败抛异常，由 Seata 调 cancel
```

TCC 使用普通 DataSource + 本地事务，不套 AT 的 `DataSourceProxy`。

三个方法分别做什么：

```text
Try：available - count，reserved + count
Confirm：reserved - count，used + count
Cancel：reserved - count，available + count
```

## 依赖

```text
JDK 17
MySQL 8
Seata Server 2.1.2（或 2.0.x）
```

启动前：

```text
1. 建库 seata_tcc_lab
2. 执行 src/main/resources/schema.sql 和 data.sql
3. 启动 Seata Server，默认 127.0.0.1:8091
4. 修改 application.yml 的数据库账号密码
```

## 请求示例

```bash
curl -X POST 'http://127.0.0.1:18081/lab/seata/tcc/orders?orderId=o-1&skuId=sku-1&userId=u-1&count=1'
```

失败路径：把 `count` 调到大于可用库存，Try 返回 false，订单事务回滚。

## 观察端点

```text
GET /lab/seata/tcc/xid              当前线程的 XID
GET /lab/seata/tcc/stock/{skuId}    available / reserved / used
GET /lab/seata/tcc/fence-log        tcc_fence_log 最近 20 条
```

观察重点：

```text
Try 后：available 减少，reserved 增加
Confirm 后：reserved 减少，used 增加
Cancel 后：reserved 减少，available 增加
tcc_fence_log：TRIED / COMMITTED / ROLLBACKED / SUSPENDED
```

## 配置文件

`src/main/resources/` 下包含 `registry.conf` 和 `file.conf`，与 `application.yml` 一起作为 Seata 客户端配置；不要只改其中一个。

## 说明

- 这是教学 lab，不是生产工程；没有鉴权、幂等业务表、对账、重试治理。
- 不要求默认启动；需要真实 MySQL 和 Seata Server。
- `useTCCFence = true` 需要 `tcc_fence_log` 表；关闭时业务要自己处理幂等/空回滚/悬挂。
