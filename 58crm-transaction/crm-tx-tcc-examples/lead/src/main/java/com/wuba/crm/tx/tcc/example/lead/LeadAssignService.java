package com.wuba.crm.tx.tcc.example.lead;

import com.wuba.crm.tx.tcc.api.TccBranch;
import com.wuba.crm.tx.tcc.api.TccRequest;
import com.wuba.crm.tx.tcc.api.TccResult;
import com.wuba.crm.tx.tcc.api.TccTransactionTemplate;
import com.wuba.crm.tx.tcc.api.TccTryResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 业务方 TCC 编排示例。
 *
 * <p>业务代码只构造 TccRequest 和 Try 回调；begin/register/report/commit
 * 全部由 DefaultTccTransactionTemplate 处理。</p>
 */
@Service
public class LeadAssignService {

    private final TccTransactionTemplate tcc;
    private final QuotaFeignClient quotaClient;
    private final String quotaCallbackEndpoint;

    /**
     * 创建编排服务。
     *
     * @param tcc                  公共 TCC 模板
     * @param quotaClient          quota OpenFeign 客户端
     * @param quotaCallbackEndpoint quota gRPC callback endpoint
     */
    public LeadAssignService(
            TccTransactionTemplate tcc,
            QuotaFeignClient quotaClient,
            @Value("${quota.callback-endpoint:127.0.0.1:9092}")
            String quotaCallbackEndpoint) {
        this.tcc = tcc;
        this.quotaClient = quotaClient;
        this.quotaCallbackEndpoint = quotaCallbackEndpoint;
    }

    /**
     * 分配线索。
     *
     * @param leadId 线索
     * @param userId 用户
     * @return 分配结果
     */
    public AssignResult assign(long leadId, long userId) {
        TccResult result = tcc.execute(TccRequest.builder()
                .requestId("lead-" + leadId + "-" + userId + "-" + UUID.randomUUID())
                .bizCode("lead")
                .bizId(String.valueOf(leadId))
                .tryTimeout(Duration.ofSeconds(30))
                .branch(TccBranch.builder()
                        .resourceKey("quota:" + userId)
                        .actionName("reserveQuota")
                        .callbackEndpoint(quotaCallbackEndpoint)
                        .applicationData("{\"userId\":" + userId + "}")
                        .tryAction(context -> {
                            TryQuotaResponse response = quotaClient.tryQuota(
                                    new TryQuotaRequest(
                                            context.xid(), context.branchId(),
                                            "reserveQuota",
                                            "{\"userId\":" + userId + "}"));
                            return response.success()
                                    ? TccTryResult.SUCCESS : TccTryResult.FAILURE;
                        })
                        .build())
                .build());
        return new AssignResult(result.xid(), result.status().name());
    }
}
