package com.wuba.crm.tx.e2e.lead;

import com.wuba.crm.tx.tcc.api.grpc.BeginRequest;
import com.wuba.crm.tx.tcc.api.grpc.BeginResponse;
import com.wuba.crm.tx.tcc.api.grpc.BranchStatus;
import com.wuba.crm.tx.tcc.api.grpc.CommitRequest;
import com.wuba.crm.tx.tcc.api.grpc.CommitResponse;
import com.wuba.crm.tx.tcc.api.grpc.RegisterBranchRequest;
import com.wuba.crm.tx.tcc.api.grpc.RegisterBranchResponse;
import com.wuba.crm.tx.tcc.api.grpc.ReportBranchRequest;
import com.wuba.crm.tx.tcc.api.grpc.ReportBranchResponse;
import com.wuba.crm.tx.tcc.api.grpc.ResultCode;
import com.wuba.crm.tx.tcc.api.grpc.RollbackRequest;
import com.wuba.crm.tx.tcc.api.grpc.RollbackResponse;
import com.wuba.crm.tx.tcc.api.grpc.TccCoordinatorGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 业务方到 Coordinator 的 gRPC 客户端。
 *
 * <p>业务方通过 Nacos 找到 Coordinator 实例，再建立 gRPC channel；
 * TC -> RM callback 仍走 gRPC，不走 OpenFeign。</p>
 */
@Component
public class TccCoordinatorClient {

    private final DiscoveryClient discoveryClient;
    private final String serviceName;
    private final long deadlineSeconds;

    /**
     * 创建客户端。
     *
     * @param discoveryClient Nacos DiscoveryClient
     * @param serviceName     Coordinator service name
     * @param deadlineSeconds 调用超时秒数
     */
    public TccCoordinatorClient(
            DiscoveryClient discoveryClient,
            @Value("${crm.tx.tcc.coordinator.service-name:crm-tx-tcc-coordinator}")
            String serviceName,
            @Value("${crm.tx.tcc.coordinator.deadline-seconds:15}") long deadlineSeconds) {
        this.discoveryClient = discoveryClient;
        this.serviceName = serviceName;
        this.deadlineSeconds = deadlineSeconds;
    }

    /**
     * 开启全局事务。
     *
     * @param request 请求
     * @return 响应
     */
    public BeginResponse begin(BeginRequest request) {
        ManagedChannel channel = channel();
        try {
            BeginResponse response = stub(channel).begin(request);
            requireOk(response.getCode(), response.getMessage());
            return response;
        } finally {
            channel.shutdownNow();
        }
    }

    /**
     * 注册分支。
     *
     * @param request 请求
     * @return 响应
     */
    public RegisterBranchResponse registerBranch(RegisterBranchRequest request) {
        ManagedChannel channel = channel();
        try {
            RegisterBranchResponse response = stub(channel).registerBranch(request);
            requireOk(response.getCode(), response.getMessage());
            return response;
        } finally {
            channel.shutdownNow();
        }
    }

    /**
     * 上报 Try 结果。
     *
     * @param xid      全局事务号
     * @param branchId 分支号
     * @param status   TRY_SUCCESS / TRY_FAIL
     * @return 响应
     */
    public ReportBranchResponse report(long xid, long branchId, BranchStatus status) {
        ManagedChannel channel = channel();
        try {
            ReportBranchResponse response = stub(channel).reportBranch(
                    ReportBranchRequest.newBuilder()
                            .setXid(xid)
                            .setBranchId(branchId)
                            .setStatus(status)
                            .build());
            requireOk(response.getCode(), response.getMessage());
            return response;
        } finally {
            channel.shutdownNow();
        }
    }

    /**
     * 提交全局事务。
     *
     * @param xid 全局事务号
     * @return 响应
     */
    public CommitResponse commit(long xid) {
        ManagedChannel channel = channel();
        try {
            CommitResponse response = stub(channel).commit(
                    CommitRequest.newBuilder().setXid(xid).build());
            requireOk(response.getCode(), response.getMessage());
            return response;
        } finally {
            channel.shutdownNow();
        }
    }

    /**
     * 回滚全局事务。
     *
     * @param xid 全局事务号
     * @return 响应
     */
    public RollbackResponse rollback(long xid) {
        ManagedChannel channel = channel();
        try {
            RollbackResponse response = stub(channel).rollback(
                    RollbackRequest.newBuilder().setXid(xid).build());
            requireOk(response.getCode(), response.getMessage());
            return response;
        } finally {
            channel.shutdownNow();
        }
    }

    private TccCoordinatorGrpc.TccCoordinatorBlockingStub stub(ManagedChannel channel) {
        return TccCoordinatorGrpc.newBlockingStub(channel)
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
    }

    private ManagedChannel channel() {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (instances == null || instances.isEmpty()) {
            throw new IllegalStateException("no coordinator instance: " + serviceName);
        }
        ServiceInstance instance = instances.get(0);
        return ManagedChannelBuilder.forAddress(instance.getHost(), instance.getPort())
                .usePlaintext()
                .build();
    }

    private void requireOk(ResultCode code, String message) {
        if (code != ResultCode.OK) {
            throw new IllegalStateException("coordinator error " + code + ": " + message);
        }
    }
}
