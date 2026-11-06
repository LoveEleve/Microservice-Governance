# BizCodeRegistry：业务线白名单

> 读完你能做什么：知道为什么不能自由传 `bizCode`，以及生产端如何阻止孤儿 Stream。

## 1. 为什么需要

如果业务方传：

```java
bizCode = "lead-typo"
```

当前会创建：

```text
tx:stream:lead-typo
OutboxBuffer 的 lead-typo 队列
```

后果：

```text
没有消费者
没有消费组
消息堆在 Redis Stream
监控看不到
```

所以必须白名单。

## 2. 配置

```java
BizCodeRegistry registry = new BizCodeRegistry(List.of(
    new BizCodeConfig("lead", 8, 500),
    new BizCodeConfig("quota", 4, 200),
    new BizCodeConfig("contract", 4, 500)
));
```

字段：

```text
bizCode   业务线编码
consumers 消费并发（消费端使用）
batchSize 单批上限
```

## 3. 发布校验

```java
public String publish(BusinessEvent event) {
    bizCodeRegistry.require(event.bizCode());
    ...
}
```

未知 `bizCode`：

```text
UnknownBizCodeException
→ 不写 t_local_message
→ 不创建 Stream
```

## 4. Stream key

```java
registry.streamKey("tx:stream:", "lead");
```

返回：

```text
tx:stream:lead
```

不要由业务方直接拼 key。

## 5. 启动配置

生产环境从 Nacos/配置中心加载：

```yaml
tx:
  stream-prefix: tx:stream:
  biz-codes:
    lead:
      consumers: 8
      batch-size: 500
    quota:
      consumers: 4
      batch-size: 200
```

启动时：

```text
加载 BizCodeRegistry
→ Publisher 使用它校验
→ Consumer 使用它创建 group/线程池
```

## 6. 当前边界

```text
已完成：
  BizCodeConfig
  BizCodeRegistry
  UnknownBizCodeException
  Publisher 发布前校验
  Registry 单元测试

未做：
  Nacos 加载
  Consumer 启动时按 registry 创建 group
  按 bizCode 独立线程池
```

## 7. 规则

```text
bizCode 必须来自注册表
不能由调用方任意传
新增业务线 = 新增配置
删除业务线 = 先停消费、再删配置
```

## 8. 自测

1. 为什么不能让业务方随便传 bizCode？
2. 未注册 bizCode 时消息会写 MySQL 吗？
3. 为什么 Stream key 要由 Registry 生成？
4. 新增业务线需要改哪些地方？
