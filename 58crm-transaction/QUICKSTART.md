# 58CRM TCC Quickstart

> 目标：5 分钟理解业务方怎么用；不要求先读 Coordinator 源码。

## 1. 两个角色

```text
TM 业务方：tcc.execute(...)
RM 参与方：实现 TccAction + @TccActionHandler
```

## 2. Maven 依赖

TM：

```xml
<dependency>
    <groupId>com.wuba.crm</groupId>
    <artifactId>crm-tx-tcc-client</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

RM：

```xml
<dependency>
    <groupId>com.wuba.crm</groupId>
    <artifactId>crm-tx-tcc-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 3. TM 发起 TCC

```java
TccResult result = tcc.execute(
        TccRequest.builder()
                .requestId("lead-assign:" + leadId + ":" + commandId)
                .bizCode("lead")
                .bizId(String.valueOf(leadId))
                .tryTimeout(Duration.ofSeconds(30))
                .branch(TccBranch.builder()
                        .resourceKey("quota:" + userId)
                        .actionName("reserveQuota")
                        .callbackEndpoint("quota-service:9092")
                        .applicationData("{\"userId\":" + userId + "}")
                        .tryAction(context -> {
                            TryQuotaResponse response = quotaClient.tryQuota(
                                    new TryQuotaRequest(
                                            context.xid(),
                                            context.branchId(),
                                            "reserveQuota",
                                            "{\"userId\":" + userId + "}"));
                            return response.success()
                                    ? TccTryResult.SUCCESS : TccTryResult.FAILURE;
                        })
                        .build())
                .build());
```

业务代码不需要：

```text
import proto
begin
registerBranch
reportBranch
commit
rollback
```

## 4. RM 实现 TCC 动作

```java
@Component
@TccActionHandler("reserveQuota")
public class QuotaTccAction implements TccAction {

    private final QuotaMapper quotaMapper;
    private final OutboxPublisher outboxPublisher;

    public QuotaTccAction(QuotaMapper quotaMapper, OutboxPublisher outboxPublisher) {
        this.quotaMapper = quotaMapper;
        this.outboxPublisher = outboxPublisher;
    }

    @Override
    public TccTryResult tryAction(BusinessActionContext context) {
        return quotaMapper.reserve(userId(context)) == 1
                ? TccTryResult.SUCCESS : TccTryResult.FAILURE;
    }

    @Override
    public TccPhaseResult confirmAction(BusinessActionContext context) {
        long userId = userId(context);
        if (quotaMapper.confirm(userId) != 1) {
            return TccPhaseResult.PERMANENT_FAILURE;
        }
        outboxPublisher.publish(new BusinessEvent(
                "quota-confirmed:" + context.xid() + ":" + context.branchId(),
                "quota",
                String.valueOf(userId),
                "QUOTA_CONFIRMED",
                "{\"userId\":" + userId + "}"));
        return TccPhaseResult.SUCCESS;
    }

    @Override
    public TccPhaseResult cancelAction(BusinessActionContext context) {
        return quotaMapper.cancel(userId(context)) == 1
                ? TccPhaseResult.SUCCESS : TccPhaseResult.PERMANENT_FAILURE;
    }

    private long userId(BusinessActionContext context) {
        String number = context.applicationData().replaceAll("[^0-9]", "");
        if (number.isEmpty()) {
            throw new IllegalArgumentException("userId missing");
        }
        return Long.parseLong(number);
    }
}
```

不需要：

```text
手写 /tcc/try controller
手写 barrier repository
手写 Confirm/Cancel gRPC server
```

## 5. 配置

TM：

```yaml
crm:
  tx:
    tcc:
      client:
        enabled: true
        coordinator-service-name: crm-tx-tcc-coordinator
```

RM：

```yaml
crm:
  tx:
    tcc:
      participant:
        enabled: true
        grpc-port: 9092
```

`client/participant` 默认都关闭，按角色显式开启。

## 6. 启动真实示例

前置：

```text
Nacos 127.0.0.1:8848
MySQL 127.0.0.1:3306
```

```bash
docker start nacos-e2e mysql-p9

docker exec -i mysql-p9 mysql -uroot -proot123 \
  < e2e/sql/schema.sql

# coordinator
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=crm_tx_e2e \
DB_USER=root DB_PASSWORD=root123 NACOS_ADDR=127.0.0.1:8848 \
NACOS_ENABLED=true \
java -jar crm-tx-tcc-coordinator/target/crm-tx-tcc-coordinator-0.1.0-SNAPSHOT.jar

# quota
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=crm_tx_e2e \
DB_USER=root DB_PASSWORD=root123 NACOS_ADDR=127.0.0.1:8848 \
java -jar crm-tx-tcc-examples/quota/target/crm-tx-tcc-example-quota-0.1.0-SNAPSHOT.jar

# lead
NACOS_ADDR=127.0.0.1:8848 QUOTA_CALLBACK_ENDPOINT=127.0.0.1:9092 \
java -jar crm-tx-tcc-examples/lead/target/crm-tx-tcc-example-lead-0.1.0-SNAPSHOT.jar
```

## 7. 验证

成功：

```bash
curl -X POST 'http://127.0.0.1:8081/leads/1001/assign?userId=1'
```

```json
{"xid":1,"status":"CONFIRMED"}
```

失败：

```bash
curl -X POST 'http://127.0.0.1:8081/leads/1001/assign?userId=999'
```

```json
{"xid":2,"status":"CANCELLED"}
```

数据库：

```text
quota assigned=1
global CONFIRMED / CANCELLED
branch CONFIRMED / CANCELLED
barrier CONFIRMED / SUSPENDED
outbox INIT（成功链路）
```

## 8. 常见错误

```text
requestId 每次不同 -> 无法业务幂等
callbackEndpoint 不可达 -> Confirm/Cancel 失败
Try 成功不 report -> TC 超时回滚
Confirm 里不写 Outbox -> 下游收不到终态
框架不会自动生成业务事件
```

## 9. 深入学习

```text
docs/07-学习讨论/00-项目学习路线.md
docs/02-设计/19-TCC时序与状态图.md
docs/02-设计/21-TCC表结构与SQL.md
docs/04-验证/23-C004.5-Review.md
```
