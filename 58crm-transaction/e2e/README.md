# TCC 真实 E2E

## 组件

```text
nacos-e2e            127.0.0.1:8848
mysql-p9             127.0.0.1:3306  root/root123
coordinator          9090，服务名 crm-tx-tcc-coordinator
lead-service         8081，服务名 lead-service（TM/业务方）
quota-service        8082，服务名 quota-service（RM1）
quota gRPC callback  9092
coupon-service       8083，服务名 coupon-service（RM2）
coupon gRPC callback 9093
```

## 启动

```bash
# 1. schema
docker exec -i mysql-p9 mysql -uroot -proot123 < e2e/sql/schema.sql

# 2. coordinator
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=crm_tx_e2e \
DB_USER=root DB_PASSWORD=root123 \
NACOS_ADDR=127.0.0.1:8848 NACOS_ENABLED=true \
TCC_GRPC_PORT=9090 \
java -jar crm-tx-tcc-coordinator/target/crm-tx-tcc-coordinator-0.1.0-SNAPSHOT.jar

# 3. quota（RM1）
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=crm_tx_e2e \
DB_USER=root DB_PASSWORD=root123 \
NACOS_ADDR=127.0.0.1:8848 \
java -jar crm-tx-tcc-examples/quota/target/crm-tx-tcc-example-quota-0.1.0-SNAPSHOT.jar

# 4. coupon（RM2）
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=crm_tx_e2e \
DB_USER=root DB_PASSWORD=root123 \
NACOS_ADDR=127.0.0.1:8848 \
java -jar crm-tx-tcc-examples/coupon/target/crm-tx-tcc-example-coupon-0.1.0-SNAPSHOT.jar

# 5. lead
NACOS_ADDR=127.0.0.1:8848 \
QUOTA_CALLBACK_ENDPOINT=127.0.0.1:9092 \
COUPON_CALLBACK_ENDPOINT=127.0.0.1:9093 \
java -jar crm-tx-tcc-examples/lead/target/crm-tx-tcc-example-lead-0.1.0-SNAPSHOT.jar
```

## 场景

```text
begin
  -> register quota  -> OpenFeign quota Try  -> TRIED
  -> register coupon -> OpenFeign coupon Try -> TRIED
  -> report 两个 TRY_SUCCESS
commit
  -> TC -> quota gRPC Confirm  -> CONFIRMED + quota 扣减 + Outbox
  -> TC -> coupon gRPC Confirm -> CONFIRMED + coupon 核销 + Outbox
```

## 成功调用

```bash
curl -X POST 'http://127.0.0.1:8081/leads/1001/assign?userId=1&couponId=1'
```

返回：

```json
{"xid":1,"status":"CONFIRMED"}
```

DB：

```text
t_quota:      user_id=1, reserved=0, assigned=1
t_coupon:     coupon_id=1, USED
t_tcc_global: xid=1, CONFIRMED
t_tcc_branch: xid=1, branch_id=1, quota:1,  CONFIRMED
              xid=1, branch_id=2, coupon:1, CONFIRMED
t_branch_barrier: xid=1, branch_id=1, CONFIRMED
                  xid=1, branch_id=2, CONFIRMED
t_local_message: quota-confirmed:1:1, INIT
                 coupon-confirmed:1:2, INIT
```

## 失败调用：quota 成功，coupon 失败

`couponId=999` 已经是 `USED`，Try 失败；quota 的 Try 已经成功，
必须被 Cancel 释放。

```bash
curl -X POST 'http://127.0.0.1:8081/leads/1001/assign?userId=1&couponId=999'
```

返回：

```json
{"xid":2,"status":"CANCELLED"}
```

DB：

```text
t_quota:      user_id=1, reserved=0, assigned 未增加
t_coupon:     coupon_id=999, USED（未变）
t_tcc_global: xid=2, CANCELLED
t_tcc_branch: xid=2, branch_id=1, quota:1,   CANCELLED（Try 成功后回滚）
              xid=2, branch_id=2, coupon:999, CANCELLED
t_branch_barrier: xid=2, branch_id=1, CANCELLED（真实 Cancel）
                  xid=2, branch_id=2, SUSPENDED（Try 失败后的空回滚）
```

这才是 TCC 的关键链路：**A 成功、B 失败、TC 撤销 A**。

## 调用协议

```text
TM -> quota/coupon Try：OpenFeign（业务 API）
TM -> TC：gRPC
TC -> quota/coupon Confirm/Cancel：gRPC callback
```

TCC callback 不使用 OpenFeign，因为它的接口契约是 gRPC proto。

> 示例让 quota/coupon 共用一个 `crm_tx_e2e` 数据库，只为了启动简单。
> 生产上每个 RM 有自己的库，`t_branch_barrier` 属于 RM 本地事务。
