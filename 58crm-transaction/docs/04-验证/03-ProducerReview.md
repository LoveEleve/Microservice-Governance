# 生产端 Review（C-001.2）

> 结论：真实链路已跑通。P0/P1 已修复；P2 仅保留监控指标待接入。
> 验证：29 tests（含真实 MySQL + Redis 7）。

## 1. 当前结构

```text
OutboxScanner（1 线程）
  │
  ▼
OutboxBuffer（按 bizCode 分桶）
  ├── lead
  ├── quota
  └── contract
  │
  ├── Sender shard-0 → lead
  ├── Sender shard-1 → quota
  └── Sender shard-2 → contract
        │
        ▼
  Redis Stream
```

## 2. P0：单条消息超过 byte 容量会永久阻塞（已修）

修复：`OutboxBuffer.put` 对 `bytes > maxBytes` 立即抛 `MessageTooLargeException`，
Scanner 捕获后直接标记 DEAD，不再无限等待。

位置：

```text
OutboxBuffer.put()
```

当前：

```java
long bytes = estimateBytes(record);
while (messageCount >= maxMessages || byteCount + bytes > maxBytes) {
    notFull.await();
}
```

如果：

```text
bytes > maxBytes
```

则：

```text
byteCount + bytes > maxBytes 永远成立
没有消息能进入 buffer
Sender 永远没有消息可发
Scanner 永久阻塞
```

这是死锁。

正确：

```text
单条消息超过 maxBytes
→ 直接拒绝/进入 DEAD/告警
不是无限等待
```

---

## 3. P1：一个 poison 消息拖死整批（已修）

修复：发送失败按二分拆批，最终定位到单条消息；好消息继续成功，坏消息单独失败/DEAD。

位置：

```text
OutboxSender.publish()
```

当前失败处理：

```text
publisher.publish 抛异常
→ markFailedBatch(整批)
```

如果一批 256 条里只有一条：

```text
序列化错误
字段非法
Stream key 错误
```

整批都会：

```text
重试
重试
重试
→ DEAD
```

好消息被坏消息拖死。

需要：

```text
能区分单条失败
或拆批重试
或至少记录坏消息 msgId
```

---

## 4. P1：Pipeline 部分成功语义不完整（已确认并保留至少一次语义）

`RedisStreamPublisherAdapter` 使用：

```text
executePipelined
```

Redis pipeline 不是原子事务：

```text
可能前几条 XADD 成功
后几条失败
连接中途断开
```

当前：

```text
只要抛异常
整批按失败
→ 部分已成功的消息会重复 XADD
```

这符合至少一次，但需要明确：

```text
重试整批
消费方 msgId 幂等
```

并且：

```text
单条永久错误不能无限拖整批
```

---

## 5. P1：Sender 等 linger 时会被单业务线拖住（已修）

修复：linger 窗口内从 `assignedBizCodes` 的所有队列取消息，不再只等第一条的 bizCode。

位置：

```text
OutboxSender.sendOnce()
```

当前：

```text
取第一条 lead
→ 等 lead 的 linger
→ 这期间 quota 有新消息也不处理
```

一个 Sender 负责多个 bizCode 时：

```text
lead 空闲等待
quota 积压
```

需要：

```text
按 Sender 内所有 assigned bizCode 计算最早 deadline
而不是只等第一条的 bizCode
```

---

## 6. P1：Registry 的 Map key 没校验（已修）

修复：`BizCodeRegistry.from` 校验 `entry.key == config.bizCode`。

`BizCodeRegistry.from(Map<String, BizCodeConfig>)`：

```java
return new BizCodeRegistry(configs.values());
```

如果配置写成：

```text
map key = "lead"
config.bizCode = "quota"
```

实际注册的是 `quota`，key `lead` 被忽略。

应该校验：

```text
entry.key == entry.value.bizCode
```

或直接由 key 构造 config。

---

## 7. P1：Scanner 不校验 Registry（已修）

修复：Scanner 对未知 bizCode/超大消息直接 `markFailedBatch(maxRetries=1)`，不再创建孤儿队列。

发布时校验了 bizCode，但 DB 里若有历史脏数据：

```text
bizCode = lead-typo
```

Scanner 仍会：

```text
创建 lead-typo 队列
Sender 分片分配不到它
消息永远不被发送
```

Scanner 也应：

```text
registry.require(candidate.bizCode())
```

未知 bizCode：

```text
丢弃 + 告警
或转 DEAD
```

---

## 8. P2：配置与装配仍有隐式约定（已修）

新增推荐工厂入口，由工厂用 `config.scanBatch()` 创建 Scanner，避免 scanBatch 分叉。

`OutboxProducerFactory` 使用：

```text
config.senderThreads()
registry.bizCodes()
```

但 Scanner 是调用方自己创建的：

```text
OutboxScanner(..., scanBatch)
```

`config.scanBatch` 并没有自动传给 Scanner。

容易出现：

```text
config.scanBatch=1000
实际 Scanner 用 100
```

需要统一工厂入口。

---

## 9. P2：生命周期与指标缺口（部分修复）

`close` 已增加 5 秒等待；Sender/队列/Stream 指标仍未接入。

```text
OutboxProducer.close 不等待线程退出
interceptors.close 可能和发送线程并发
没有 Sender 每分片吞吐/失败指标
没有 buffer 深度/等待时间指标
没有 Stream XADD 延迟指标
```

## 10. 测试缺口

```text
P0 超大批次/超大消息
P1 pipeline 部分成功
P1 poison message
P1 一个 Sender 多队列的 linger 公平性
P1 Redis 断开/恢复
P2 Producer start/close
P2 多 Sender 实际发送
```

## 11. Review 结论

```text
可继续开发消费端
但不能把生产端标成生产可用
```

必须先修：

```text
P0 超 byte 容量死锁
P1 poison 消息隔离
P1 linger 多队列调度
P1 Registry/Scanner 校验
```

再补：

```text
pipeline 部分失败测试
真实 Redis 故障演练
生产指标
```


## 12. 第二轮 review：剩余风险

### P1：Redis 全挂时二分拆批会放大 DB 重试

当前 poison 隔离策略：

```text
整批失败
→ 二分
→ 继续二分
→ 到单条
```

如果 Redis 整体不可用：

```text
256 条 → 约 255 次拆批/失败处理
→ 大量数据库重试更新
```

需要区分：

```text
连接级故障 → 整批失败退避
消息级故障 → 二分定位
```

### P1：Pipeline 部分成功没有专项测试

当前接受：

```text
部分 XADD 成功
→ 整批重试
→ 消费方幂等
```

但没有真实 Redis 断连/部分失败测试。

### P2：监控指标仍未接入

```text
扫描量
发送量
失败量
buffer 深度
linger 等待时间
XADD 延迟
DEAD 数量
```

### P2：测试缺口

```text
buffer 满载阻塞
OutboxProducer 生命周期
多 Sender 实际并发发送
Redis 断连恢复
```

## 13. 复审结论

```text
类/方法文档已覆盖生产端公开 API
真实主链路已通过
P0 已修
P1 主路径已修
但仍不能声称“全部生产风险已关闭”
```
