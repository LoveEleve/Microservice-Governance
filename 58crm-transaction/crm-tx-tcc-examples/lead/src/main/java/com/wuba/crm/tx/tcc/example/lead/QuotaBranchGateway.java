package com.wuba.crm.tx.tcc.example.lead;

import com.wuba.crm.tx.tcc.api.TccBranch;
import com.wuba.crm.tx.tcc.api.TccContext;
import com.wuba.crm.tx.tcc.api.TccTryResult;
import org.springframework.stereotype.Service;

/**
 * 分支网关：注解负责注册/上报，内部通过 OpenFeign 调 quota Try。
 */
@Service
public class QuotaBranchGateway {

    private final QuotaFeignClient quotaClient;

    /**
     * 创建网关。
     *
     * @param quotaClient quota Feign
     */
    public QuotaBranchGateway(QuotaFeignClient quotaClient) {
        this.quotaClient = quotaClient;
    }

    /**
     * 调用 quota Try。
     *
     * @param userId 用户
     * @return Try 结果
     */
    @TccBranch(
            actionName = "reserveQuota",
            resourceKey = "'quota:' + #userId",
            callbackEndpoint = "${quota.callback-endpoint}",
            applicationData = "#userId")
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
