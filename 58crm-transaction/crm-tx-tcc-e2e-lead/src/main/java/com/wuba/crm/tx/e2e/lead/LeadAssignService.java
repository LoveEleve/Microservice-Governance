package com.wuba.crm.tx.e2e.lead;

import com.wuba.crm.tx.tcc.api.grpc.BeginRequest;
import com.wuba.crm.tx.tcc.api.grpc.BeginResponse;
import com.wuba.crm.tx.tcc.api.grpc.BranchStatus;
import com.wuba.crm.tx.tcc.api.grpc.CommitResponse;
import com.wuba.crm.tx.tcc.api.grpc.RegisterBranchRequest;
import com.wuba.crm.tx.tcc.api.grpc.RegisterBranchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 业务方 TCC 编排示例。
 *
 * <p>Try 通过 OpenFeign 调用 quota-service；TC 的 Confirm/Cancel
 * callback 仍通过 gRPC 调用 quota 的 participant server。</p>
 */
@Service
public class LeadAssignService {

    private final TccCoordinatorClient coordinator;
    private final QuotaFeignClient quotaClient;
    private final String quotaCallbackEndpoint;

    /**
     * 创建编排服务。
     *
     * @param coordinator           Coordinator gRPC 客户端
     * @param quotaClient           quota OpenFeign 客户端
     * @param quotaCallbackEndpoint quota gRPC callback endpoint
     */
    public LeadAssignService(
            TccCoordinatorClient coordinator,
            QuotaFeignClient quotaClient,
            @Value("${quota.callback-endpoint:127.0.0.1:9092}")
            String quotaCallbackEndpoint) {
        this.coordinator = coordinator;
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
        String requestId = "lead-" + leadId + "-" + userId + "-" + UUID.randomUUID();
        BeginResponse begin = coordinator.begin(BeginRequest.newBuilder()
                .setRequestId(requestId)
                .setBizCode("lead")
                .setBizId(String.valueOf(leadId))
                .setTryTimeoutSeconds(30)
                .build());
        long xid = begin.getXid();

        try {
            RegisterBranchResponse branch = coordinator.registerBranch(
                    RegisterBranchRequest.newBuilder()
                            .setXid(xid)
                            .setResourceKey("quota:" + userId)
                            .setActionName("reserveQuota")
                            .setCallbackEndpoint(quotaCallbackEndpoint)
                            .setApplicationData("{\"userId\":" + userId + "}")
                            .build());

            TryQuotaResponse tryResponse = quotaClient.tryQuota(
                    new TryQuotaRequest(xid, branch.getBranchId(), userId));
            if (!tryResponse.success()) {
                coordinator.report(xid, branch.getBranchId(), BranchStatus.BRANCH_TRY_FAIL);
                coordinator.rollback(xid);
                return new AssignResult(xid, "TRY_FAIL");
            }

            coordinator.report(xid, branch.getBranchId(), BranchStatus.BRANCH_TRY_SUCCESS);
            CommitResponse commit = coordinator.commit(xid);
            return new AssignResult(xid, commit.getStatus().name());
        } catch (RuntimeException failure) {
            try {
                coordinator.rollback(xid);
            } catch (RuntimeException ignored) {
                // 保留原始失败原因。
            }
            throw failure;
        }
    }
}
