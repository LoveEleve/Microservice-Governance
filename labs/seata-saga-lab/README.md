# Seata Saga Multi-Service Lab

对应 Seata 小册第 5 章：编排服务 + 机票/酒店/租车三个服务，正向预订、失败反向补偿。

## 结构

```text
orchestrator-service  :18082   状态机引擎 + TravelBookingGateway
flight-service        :18085   机票服务
hotel-service         :18086   酒店服务
car-service           :18087   租车服务
```

状态机：

```text
travel_booking.json
  BookFlight → BookHotel → BookCar → Success

失败：
  Catch → CompensationTrigger
  → CancelHotel → CancelFlight
  → 已完成的步骤才补偿
```

`TravelBookingGateway` 负责把状态机的 `ServiceTask` 转成 HTTP 调用：

```text
bookFlight  → POST flight-service /internal/flight/book
bookHotel   → POST hotel-service  /internal/hotel/book
bookCar     → POST car-service    /internal/car/book

cancelFlight → POST flight-service /internal/flight/cancel
cancelHotel  → POST hotel-service  /internal/hotel/cancel
cancelCar    → POST car-service    /internal/car/cancel
```

## 启动顺序

```text
1. flight-service   :18085
2. hotel-service    :18086
3. car-service      :18087
4. orchestrator-service :18082
```

不需要 MySQL，也不需要 Seata TC；这个 lab 演示状态机编排和补偿链。

## 请求

成功：

```bash
curl -X POST 'http://127.0.0.1:18082/lab/seata/saga/travel?userId=u-1'
```

失败并补偿：

```bash
curl -X POST 'http://127.0.0.1:18082/lab/seata/saga/travel?userId=u-1&failCar=true'
```

观察各服务状态：

```text
GET http://127.0.0.1:18085/internal/flight/bookings
GET http://127.0.0.1:18086/internal/hotel/bookings
GET http://127.0.0.1:18087/internal/car/bookings
```

失败请求下：

```text
flight: BOOKED → CANCELLED
hotel:  BOOKED → CANCELLED
car:    没有 BOOKED（在提交前失败）
```

## 说明

- 这是教学 lab，不是生产工程；没有持久化状态机日志、幂等表、对账和人工补偿台。
- `orchestrator-service` 的 `seata.enabled=false` 只关闭 TC 集成，状态机引擎仍由 Seata Saga 提供。
- 生产使用 Seata Saga 时，应配置状态机存储、TC 高可用和补偿幂等。
