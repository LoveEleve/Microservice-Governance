# TCC 真实 E2E

## 组件

```text
nacos-e2e          127.0.0.1:8848
mysql-p9           127.0.0.1:3306  root/root123
coordinator        9090，服务名 crm-tx-tcc-coordinator
lead-service       8081，服务名 lead-service（TM/业务方）
quota-service      8082，服务名 quota-service（RM/参与方）
quota gRPC callback 9092
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

# 3. quota
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=crm_tx_e2e \
DB_USER=root DB_PASSWORD=root123 \
NACOS_ADDR=127.0.0.1:8848 \
java -jar crm-tx-tcc-e2e-quota/target/crm-tx-tcc-e2e-quota-0.1.0-SNAPSHOT.jar

# 4. lead
NACOS_ADDR=127.0.0.1:8848 \
QUOTA_CALLBACK_ENDPOINT=127.0.0.1:9092 \
java -jar crm-tx-tcc-e2e-lead/target/crm-tx-tcc-e2e-lead-0.1.0-SNAPSHOT.jar
```

## 场景

业务方 `lead-service`：

```text
begin
  -> TC gRPC
registerBranch
  -> TC gRPC
Try
  -> OpenFeign -> quota-service /quota/try
  -> quota barrier TRIED
report TRY_SUCCESS
  -> TC gRPC
commit
  -> TC gRPC
  -> TC -> quota gRPC Confirm
  -> barrier CONFIRMED
  -> quota 业务终态 + Outbox INIT
```

## 成功调用

```bash
curl -X POST 'http://127.0.0.1:8081/leads/1001/assign?userId=1'
```

返回：

```json
{"xid":1,"status":"CONFIRMED"}
```

DB：

```text
t_quota: user_id=1, reserved=0, assigned=1
t_tcc_global: xid=1, CONFIRMED
t_tcc_branch: xid=1, branch_id=1, CONFIRMED
t_branch_barrier: xid=1, branch_id=1, CONFIRMED
t_local_message: event_key=quota-confirmed:1:1, INIT
```

## Try 失败调用

```bash
curl -X POST 'http://127.0.0.1:8081/leads/1001/assign?userId=999'
```

返回：

```json
{"xid":2,"status":"CANCELLED"}
```

DB：

```text
t_tcc_global: xid=2, CANCELLED
t_tcc_branch: xid=2, CANCELLED
t_branch_barrier: xid=2, SUSPENDED
```

说明空回滚生效。

## 调用协议

```text
业务方 -> quota Try：OpenFeign
业务方 -> TC：gRPC
TC -> quota Confirm/Cancel：gRPC callback
```

TCC callback 不使用 OpenFeign，因为它的接口契约是 gRPC proto。
