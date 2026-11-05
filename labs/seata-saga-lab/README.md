# Seata Saga Lab

对应 Seata 小册第 5 章：用状态机编排旅行预订，并在失败时反向补偿。

## 这个 lab 展示什么

```text
travel_booking.json
  BookFlight → BookHotel → BookCar → Success

失败：
  Catch → CompensationTrigger
  → 补偿已完成的步骤（通常是 CancelHotel → CancelFlight）
  → BookCar 若在提交前失败，不会执行 CancelCar
```

`TravelBookingService` 提供：

```text
bookFlight / cancelFlight
bookHotel  / cancelHotel
bookCar    / cancelCar
```

`SagaConfig` 创建 `StateMachineEngine`，加载 `statelang/*.json`。

## 依赖

```text
JDK 17
Seata 2.0.0 客户端
Seata Server（如需走 TC；本 lab 主要展示状态机编排）
```

这个 lab 不依赖 MySQL；状态机定义从 classpath 加载，服务用内存 Map 记录预订状态。

## 请求示例

成功：

```bash
curl -X POST 'http://127.0.0.1:18082/lab/seata/saga/travel?userId=u-1'
```

失败并触发补偿：

```bash
curl -X POST 'http://127.0.0.1:18082/lab/seata/saga/travel?userId=u-1&failCar=true'
```

返回：

```text
instance.status
bookings：flight/hotel/car 的 BOOKED/CANCELLED
```

## 说明

- 这是教学 lab，不是生产工程；没有持久化状态机日志、幂等表、对账和重试治理。
- 不要求默认启动；状态机引擎、ServiceTask 调用和补偿链是重点。
- 生产使用 Seata Saga 时，需要配置状态机存储、TC 和补偿幂等。
