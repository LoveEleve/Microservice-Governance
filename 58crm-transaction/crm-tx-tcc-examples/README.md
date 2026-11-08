# TCC Examples

## 模块

```text
lead   业务方 / TM，OpenFeign 调 quota/coupon Try
quota  参与方 / RM1，barrier + quota 业务 + Outbox
coupon 参与方 / RM2，barrier + coupon 业务 + Outbox
```

## 启动依赖

```text
Nacos 8848
MySQL 3306
Coordinator 9090
```

## 启动

```bash
# schema
docker exec -i mysql-p9 mysql -uroot -proot123 < e2e/sql/schema.sql

# coordinator
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=crm_tx_e2e \
DB_USER=root DB_PASSWORD=root123 \
NACOS_ADDR=127.0.0.1:8848 NACOS_ENABLED=true \
java -jar crm-tx-tcc-coordinator/target/crm-tx-tcc-coordinator-0.1.0-SNAPSHOT.jar

# quota
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=crm_tx_e2e \
DB_USER=root DB_PASSWORD=root123 \
NACOS_ADDR=127.0.0.1:8848 \
java -jar crm-tx-tcc-examples/quota/target/crm-tx-tcc-example-quota-0.1.0-SNAPSHOT.jar

# coupon
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=crm_tx_e2e \
DB_USER=root DB_PASSWORD=root123 \
NACOS_ADDR=127.0.0.1:8848 \
java -jar crm-tx-tcc-examples/coupon/target/crm-tx-tcc-example-coupon-0.1.0-SNAPSHOT.jar

# lead
NACOS_ADDR=127.0.0.1:8848 \
QUOTA_CALLBACK_ENDPOINT=127.0.0.1:9092 \
COUPON_CALLBACK_ENDPOINT=127.0.0.1:9093 \
java -jar crm-tx-tcc-examples/lead/target/crm-tx-tcc-example-lead-0.1.0-SNAPSHOT.jar
```

## 调用

```bash
# 成功：两个 RM 都 Try 成功
curl -X POST 'http://127.0.0.1:8081/leads/1001/assign?userId=1&couponId=1'

# 失败：quota Try 成功，coupon Try 失败，quota 被 Cancel
curl -X POST 'http://127.0.0.1:8081/leads/1001/assign?userId=1&couponId=999'
```

## 业务代码

```text
lead:   @TccTransactional + 两个 @TccBranch（quota/coupon）+ OpenFeign Try
quota:  TccAction + @TccActionHandler
coupon: TccAction + @TccActionHandler
```

完整链路见根目录 `e2e/README.md`。
