# crm-tx-demo

业务方使用示例模块。

## 代码

```text
AssignLeadCommand   业务请求
LeadRepository      业务表写接口
LeadAssignService   业务方接入点
```

## 核心代码

```java
@Transactional
public void assign(AssignLeadCommand command) {
    leadRepository.assign(command.leadId(), command.salesId());
    outboxPublisher.publish(new BusinessEvent(...));
}
```

业务方只做两件事：

```text
写业务表
写 Outbox 事件
```

## 测试

```bash
mvn -pl crm-tx-demo -am test
```

## 注意

这是业务接入示例，不是框架内部实现。  
真实项目里 `LeadRepository` 换成 MyBatis-Plus Mapper，`OutboxPublisher` 由 `MybatisOutboxPublisher` 实现。
