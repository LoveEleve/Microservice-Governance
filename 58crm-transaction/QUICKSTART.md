# 58CRM TCC Quickstart

> 目标：5 分钟理解业务方怎么用；不要求先读 Coordinator 源码。
> 默认写法是指注解法；`TccRequest.builder()` 只作为高级逃生口保留。

## 1. 三个角色

```text
TM 业务方：@TccTransactional + 两个 @TccBranch
RM1 quota：TccAction + @TccActionHandler("reserveQuota")
RM2 coupon：TccAction + @TccActionHandler("lockCoupon")
```

Lead 只做 TM，不实现 TccAction；Confirm/Cancel 在 quota/coupon 的 RM 动作里。

## 2. Maven 依赖

先 import BOM，业务依赖不再写版本：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.wuba.crm</groupId>
            <artifactId>crm-tx-bom</artifactId>
            <version>0.1.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

TM：

```xml
<dependency>
    <groupId>com.wuba.crm</groupId>
    <artifactId>crm-tx-tcc-spring-boot-starter</artifactId>
</dependency>
```

RM：

```xml
<dependency>
    <groupId>com.wuba.crm</groupId>
    <artifactId>crm-tx-tcc-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.wuba.crm</groupId>
    <artifactId>crm-tx-tcc-participant</artifactId>
</dependency>
```

> starter 把 participant 标为 optional：TM-only 应用不会被 MyBatis/DataSource 依赖污染；RM 显式加 participant。

## 3. TM 发起 TCC

业务方法只写注解，不碰 begin/register/report/commit：

```java
@Service
public class LeadAssignService {

    private final QuotaBranchGateway quotaGateway;
    private final CouponBranchGateway couponGateway;

    public LeadAssignService(
            QuotaBranchGateway quotaGateway, CouponBranchGateway couponGateway) {
        this.quotaGateway = quotaGateway;
        this.couponGateway = couponGateway;
    }

    @TccTransactional(
            requestId = "#{#command.requestId}",
            bizCode = "lead",
            bizId = "#{#command.leadId}",
            tryTimeoutSeconds = 30)
    public TccResult assign(AssignLeadCommand command) {
        quotaGateway.reserve(command.userId());
        couponGateway.lock(command.couponId());
        return null;
    }
}
```

分支放在独立 Spring Bean 的方法上，方法体只负责调下游 Try：

```java
@Service
public class QuotaBranchGateway {

    private final QuotaFeignClient quotaClient;

    public QuotaBranchGateway(QuotaFeignClient quotaClient) {
        this.quotaClient = quotaClient;
    }

    @TccBranch(
            actionName = "reserveQuota",
            resourceKey = "quota:#{#userId}",
            callbackEndpoint = "${quota.callback-endpoint}",
            applicationData = "#{#userId}")
    public TccTryResult reserve(long userId) {
        TccContext context = TccContext.current();
        TryQuotaResponse response = quotaClient.tryQuota(new TryQuotaRequest(
                context.xid(),
                context.branchId(),
                "reserveQuota",
                "{\"userId\":" + userId + "}"));
        return response.success() ? TccTryResult.SUCCESS : TccTryResult.FAILURE;
    }
}
```

第二个 RM 同理：

```java
@Service
public class CouponBranchGateway {

    private final CouponFeignClient couponClient;

    public CouponBranchGateway(CouponFeignClient couponClient) {
        this.couponClient = couponClient;
    }

    @TccBranch(
            actionName = "lockCoupon",
            resourceKey = "coupon:#{#couponId}",
            callbackEndpoint = "${coupon.callback-endpoint}",
            applicationData = "#{#couponId}")
    public TccTryResult lock(long couponId) {
        TccContext context = TccContext.current();
        TryCouponResponse response = couponClient.tryCoupon(new TryCouponRequest(
                context.xid(),
                context.branchId(),
                "lockCoupon",
                "{\"couponId\":" + couponId + "}"));
        return response.success() ? TccTryResult.SUCCESS : TccTryResult.FAILURE;
    }
}
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

`requestId` 必须是稳定业务幂等键（如 `commandId`）；示例 controller 每次点击生成 UUID，只是为了区分多次分配尝试。

注解值规则：

```text
#{...} 是表达式边界，内部用 #变量，例如 "#{#userId}"
表达式外的 # 原样保留，例如 "order:#123" 不会被解析
旧写法 "#userId"、"'quota:' + #userId" 仍兼容
循环分支必须保证 resourceKey 唯一；重复 key 会直接失败并回滚
```

## 4. RM 实现 TCC 动作（每个 RM 一个）

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

coupon 的 RM 动作同样实现三个方法；Confirm/Cancel 在这里，不在 Lead：

```java
@TccActionHandler("lockCoupon")
public class CouponTccAction implements TccAction {

    @Override
    public TccTryResult tryAction(BusinessActionContext context) {
        return couponMapper.lock(couponId(context)) == 1
                ? TccTryResult.SUCCESS : TccTryResult.FAILURE;
    }

    @Override
    public TccPhaseResult confirmAction(BusinessActionContext context) {
        long couponId = couponId(context);
        if (couponMapper.confirm(couponId) != 1) {
            return TccPhaseResult.PERMANENT_FAILURE;
        }
        outboxPublisher.publish(new BusinessEvent(
                "coupon-confirmed:" + context.xid() + ":" + context.branchId(),
                "coupon",
                String.valueOf(couponId),
                "COUPON_USED",
                "{\"couponId\":" + couponId + "}"));
        return TccPhaseResult.SUCCESS;
    }

    @Override
    public TccPhaseResult cancelAction(BusinessActionContext context) {
        return couponMapper.cancel(couponId(context)) == 1
                ? TccPhaseResult.SUCCESS : TccPhaseResult.PERMANENT_FAILURE;
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
      participant:
        enabled: false
      annotation:
        enabled: true
```

RM：

```yaml
crm:
  tx:
    tcc:
      participant:
        enabled: true
        grpc-port: 9092   # coupon 用 9093
```

`client/participant` 默认都关闭，按角色显式开启；每个 RM 一个 callback 端口。

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

# coupon
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=crm_tx_e2e \
DB_USER=root DB_PASSWORD=root123 NACOS_ADDR=127.0.0.1:8848 \
java -jar crm-tx-tcc-examples/coupon/target/crm-tx-tcc-example-coupon-0.1.0-SNAPSHOT.jar

# lead
NACOS_ADDR=127.0.0.1:8848 \
QUOTA_CALLBACK_ENDPOINT=127.0.0.1:9092 \
COUPON_CALLBACK_ENDPOINT=127.0.0.1:9093 \
java -jar crm-tx-tcc-examples/lead/target/crm-tx-tcc-example-lead-0.1.0-SNAPSHOT.jar
```

## 7. 验证

成功：

```bash
curl -X POST 'http://127.0.0.1:8081/leads/1001/assign?userId=1&couponId=1'
```

```json
{"xid":1,"status":"CONFIRMED"}
```

失败：

```bash
curl -X POST 'http://127.0.0.1:8081/leads/1001/assign?userId=1&couponId=999'
```

```json
{"xid":2,"status":"CANCELLED"}
```

Try 失败是业务结果，不是 HTTP 500：注解事务返回 `TccResult(CANCELLED)`。

数据库：

```text
quota assigned=1 / coupon USED（成功）
失败时 quota reserved 回到 0，quota barrier=CANCELLED
coupon Try 失败，coupon barrier=SUSPENDED
global CONFIRMED / CANCELLED
outbox INIT（成功链路，quota + coupon 各一条）
```

## 8. 常见错误

```text
requestId 每次不同 -> 无法业务幂等
callbackEndpoint 不可达 -> Confirm/Cancel 失败
Try 成功不 report -> TC 超时回滚
Confirm 里不写 Outbox -> 下游收不到终态
框架不会自动生成业务事件
@TccBranch 写在同类内部调用 -> AOP 不生效
resourceKey 重复 -> 同一事务内分支键冲突，直接回滚
字面量 # 写进表达式且不以引号/模板包住 -> SpEL 解析错误
```

## 9. 深入学习

```text
docs/07-学习讨论/00-项目学习路线.md
docs/02-设计/19-TCC时序与状态图.md
docs/02-设计/21-TCC表结构与SQL.md
docs/04-验证/28-C006-Multi-Branch-Spel-Review.md
```
