# Seata Saga Lab

对应 Seata 小册第 5 章：一个编排服务 + 本地 DB + Seata TC。

## 结构

```text
saga-orchestrator-service  :18082
  ├── StateMachineEngine（Seata Saga）
  ├── TravelBookingService（本地 DB）
  └── travel_booking.json
Seata TC                   :8091
MySQL                      seata_saga_lab
```

这版按官方执行模型：

```text
状态机引擎
  → 开启全局事务，拿 XID
  → 执行 ServiceTask
  → 本地 DB 记录状态机实例/状态
  → 向 TC 注册 SAGA 分支/上报状态
  → 失败时 CompensationTrigger
  → TC 二阶段 branchRollback
```

机票/酒店/租车不是独立服务，而是状态机里的 `ServiceTask`，由本地 `TravelBookingService` 操作本地三张表：

```text
flight_booking
hotel_booking
car_booking
```

## 关键配置

```yaml
seata:
  enabled: true
  saga:
    enabled: true
    state-machine:
      resources:
        - classpath*:statelang/*.json
```

`seata.enabled=true` 接 TC，`seata.saga.enabled=true` 让 Starter 自动创建：

```text
DbStateMachineConfig
StateMachineEngine
DbAndReportTcStateLogStore
```

## 启动前

```text
1. 建库 seata_saga_lab
2. 启动 Seata Server，默认 127.0.0.1:8091
3. 修改 application.yml 的数据库账号密码
4. schema.sql 会自动建业务表 + 状态机三张表
```

## 请求

成功：

```bash
curl -X POST 'http://127.0.0.1:18082/lab/seata/saga/travel?userId=u-1'
```

失败并补偿：

```bash
curl -X POST 'http://127.0.0.1:18082/lab/seata/saga/travel?userId=u-1&failCar=true'
```

观察：

```text
GET /lab/seata/saga/bookings
GET /lab/seata/saga/state-instances
GET /lab/seata/saga/state-nodes
```

## 失败路径

```text
BookFlight 成功：flight_booking = BOOKED
BookHotel  成功：hotel_booking = BOOKED
BookCar    失败：car_booking 没有记录
CompensationTrigger
→ cancelHotel：hotel_booking = CANCELLED
→ cancelFlight：flight_booking = CANCELLED
```

## 说明

- 这是教学 lab，不是生产工程；没有幂等表、对账、人工补偿台。
- 生产使用 Seata Saga 时，需要配置 TC 高可用、状态机存储、补偿重试和人工处理。
