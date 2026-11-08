package com.wuba.crm.tx.tcc.client.internal;

import com.wuba.crm.tx.tcc.client.TccCoordinatorEndpointProvider;
import com.wuba.crm.tx.tcc.api.BranchStatus;
import com.wuba.crm.tx.tcc.api.GlobalStatus;
import com.wuba.crm.tx.tcc.protocol.model.TccBeginRequest;
import com.wuba.crm.tx.tcc.protocol.model.TccBeginResult;
import com.wuba.crm.tx.tcc.protocol.model.TccBranchRegisterRequest;
import com.wuba.crm.tx.tcc.protocol.model.TccBranchRegisterResult;
import com.wuba.crm.tx.tcc.protocol.model.TccBranchReportRequest;
import com.wuba.crm.tx.tcc.protocol.model.TccCommitResult;
import com.wuba.crm.tx.tcc.api.TccException;
import com.wuba.crm.tx.tcc.api.TccResultCode;
import com.wuba.crm.tx.tcc.protocol.model.TccRollbackResult;
import com.wuba.crm.tx.tcc.protocol.grpc.BeginRequest;
import com.wuba.crm.tx.tcc.protocol.grpc.CommitRequest;
import com.wuba.crm.tx.tcc.protocol.grpc.ReportBranchRequest;
import com.wuba.crm.tx.tcc.protocol.grpc.ResultCode;
import com.wuba.crm.tx.tcc.protocol.grpc.RollbackRequest;
import com.wuba.crm.tx.tcc.protocol.grpc.TccCoordinatorGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

/**
 * 基于 gRPC 的 Coordinator 客户端实现。
 *
 * <p>这是 client 内部协议实现；业务方只依赖
 * {@link com.wuba.crm.tx.tcc.api.TccTransactionTemplate}。</p>
 */
public class CoordinatorGrpcClient implements TccCoordinatorOperations {

    private final TccCoordinatorEndpointProvider endpointProvider;
    private final long deadlineSeconds;

    /**
     * 创建客户端。
     *
     * @param endpointProvider endpoint 提供者
     * @param deadlineSeconds  调用超时秒数
     */
    public CoordinatorGrpcClient(
            TccCoordinatorEndpointProvider endpointProvider,
            long deadlineSeconds) {
        if (endpointProvider == null) {
            throw new IllegalArgumentException("endpointProvider must not be null");
        }
        if (deadlineSeconds <= 0) {
            throw new IllegalArgumentException("deadlineSeconds must be positive");
        }
        this.endpointProvider = endpointProvider;
        this.deadlineSeconds = deadlineSeconds;
    }

    /** {@inheritDoc} */
    @Override
    public TccBeginResult begin(TccBeginRequest request) {
        ManagedChannel channel = channel();
        try {
            var response = stub(channel).begin(BeginRequest.newBuilder()
                    .setRequestId(request.requestId())
                    .setBizCode(request.bizCode())
                    .setBizId(request.bizId())
                    .setTryTimeoutSeconds(request.tryTimeoutSeconds())
                    .build());
            requireOk(response.getCode(), response.getMessage());
            return new TccBeginResult(
                    response.getXid(), fromProto(response.getStatus()));
        } finally {
            channel.shutdownNow();
        }
    }

    /** {@inheritDoc} */
    @Override
    public TccBranchRegisterResult registerBranch(TccBranchRegisterRequest request) {
        ManagedChannel channel = channel();
        try {
            var response = stub(channel).registerBranch(
                    com.wuba.crm.tx.tcc.protocol.grpc.RegisterBranchRequest.newBuilder()
                            .setXid(request.xid())
                            .setResourceKey(request.resourceKey())
                            .setActionName(request.actionName())
                            .setCallbackEndpoint(request.callbackEndpoint())
                            .setApplicationData(request.applicationData())
                            .build());
            requireOk(response.getCode(), response.getMessage());
            return new TccBranchRegisterResult(
                    response.getBranchId(), fromProto(response.getStatus()));
        } finally {
            channel.shutdownNow();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void reportBranch(TccBranchReportRequest request) {
        ManagedChannel channel = channel();
        try {
            var response = stub(channel).reportBranch(ReportBranchRequest.newBuilder()
                    .setXid(request.xid())
                    .setBranchId(request.branchId())
                    .setStatus(toProto(request.status()))
                    .setMessage(request.message() == null ? "" : request.message())
                    .build());
            requireOk(response.getCode(), response.getMessage());
        } finally {
            channel.shutdownNow();
        }
    }

    /** {@inheritDoc} */
    @Override
    public TccCommitResult commit(long xid) {
        ManagedChannel channel = channel();
        try {
            var response = stub(channel).commit(
                    CommitRequest.newBuilder().setXid(xid).build());
            requireOk(response.getCode(), response.getMessage());
            return new TccCommitResult(fromProto(response.getStatus()));
        } finally {
            channel.shutdownNow();
        }
    }

    /** {@inheritDoc} */
    @Override
    public TccRollbackResult rollback(long xid) {
        ManagedChannel channel = channel();
        try {
            var response = stub(channel).rollback(
                    RollbackRequest.newBuilder().setXid(xid).build());
            requireOk(response.getCode(), response.getMessage());
            return new TccRollbackResult(fromProto(response.getStatus()));
        } finally {
            channel.shutdownNow();
        }
    }

    private TccCoordinatorGrpc.TccCoordinatorBlockingStub stub(ManagedChannel channel) {
        return TccCoordinatorGrpc.newBlockingStub(channel)
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
    }

    private ManagedChannel channel() {
        String target = endpointProvider.target();
        if (target == null || target.isBlank()) {
            throw new IllegalStateException("empty coordinator target");
        }
        return ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();
    }

    private void requireOk(ResultCode code, String message) {
        if (code != ResultCode.OK) {
            throw new TccException(fromProto(code),
                    message == null ? code.name() : message);
        }
    }

    private TccResultCode fromProto(ResultCode code) {
        return switch (code) {
            case OK -> TccResultCode.OK;
            case TCC_INVALID_ARGUMENT -> TccResultCode.TCC_INVALID_ARGUMENT;
            case TCC_NOT_READY -> TccResultCode.TCC_NOT_READY;
            case TCC_INVALID_STATE -> TccResultCode.TCC_INVALID_STATE;
            case TCC_BUSY -> TccResultCode.TCC_BUSY;
            case TCC_NOT_FOUND -> TccResultCode.TCC_NOT_FOUND;
            case TCC_DUPLICATE -> TccResultCode.TCC_DUPLICATE;
            case TCC_RETRY_EXHAUSTED -> TccResultCode.TCC_RETRY_EXHAUSTED;
            case TCC_CALLBACK_FAILED -> TccResultCode.TCC_CALLBACK_FAILED;
            case TCC_INTERNAL_ERROR -> TccResultCode.TCC_INTERNAL_ERROR;
            default -> TccResultCode.TCC_INTERNAL_ERROR;
        };
    }

    private GlobalStatus fromProto(com.wuba.crm.tx.tcc.protocol.grpc.GlobalStatus status) {
        return switch (status) {
            case TRYING -> GlobalStatus.TRYING;
            case CONFIRMING -> GlobalStatus.CONFIRMING;
            case CONFIRMED -> GlobalStatus.CONFIRMED;
            case CANCELLING -> GlobalStatus.CANCELLING;
            case CANCELLED -> GlobalStatus.CANCELLED;
            case FAILED -> GlobalStatus.FAILED;
            default -> throw new IllegalArgumentException("unknown global status: " + status);
        };
    }

    private BranchStatus fromProto(
            com.wuba.crm.tx.tcc.protocol.grpc.BranchStatus status) {
        return switch (status) {
            case BRANCH_TRYING -> BranchStatus.TRYING;
            case BRANCH_TRY_SUCCESS -> BranchStatus.TRY_SUCCESS;
            case BRANCH_TRY_FAIL -> BranchStatus.TRY_FAIL;
            case BRANCH_CONFIRMING -> BranchStatus.CONFIRMING;
            case BRANCH_CONFIRMED -> BranchStatus.CONFIRMED;
            case BRANCH_CANCELLING -> BranchStatus.CANCELLING;
            case BRANCH_CANCELLED -> BranchStatus.CANCELLED;
            case BRANCH_SUSPENDED -> BranchStatus.SUSPENDED;
            case BRANCH_DEAD -> BranchStatus.DEAD;
            default -> throw new IllegalArgumentException("unknown branch status: " + status);
        };
    }

    private com.wuba.crm.tx.tcc.protocol.grpc.BranchStatus toProto(BranchStatus status) {
        return switch (status) {
            case TRYING -> com.wuba.crm.tx.tcc.protocol.grpc.BranchStatus.BRANCH_TRYING;
            case TRY_SUCCESS -> com.wuba.crm.tx.tcc.protocol.grpc.BranchStatus.BRANCH_TRY_SUCCESS;
            case TRY_FAIL -> com.wuba.crm.tx.tcc.protocol.grpc.BranchStatus.BRANCH_TRY_FAIL;
            case CONFIRMING -> com.wuba.crm.tx.tcc.protocol.grpc.BranchStatus.BRANCH_CONFIRMING;
            case CONFIRMED -> com.wuba.crm.tx.tcc.protocol.grpc.BranchStatus.BRANCH_CONFIRMED;
            case CANCELLING -> com.wuba.crm.tx.tcc.protocol.grpc.BranchStatus.BRANCH_CANCELLING;
            case CANCELLED -> com.wuba.crm.tx.tcc.protocol.grpc.BranchStatus.BRANCH_CANCELLED;
            case SUSPENDED -> com.wuba.crm.tx.tcc.protocol.grpc.BranchStatus.BRANCH_SUSPENDED;
            case DEAD -> com.wuba.crm.tx.tcc.protocol.grpc.BranchStatus.BRANCH_DEAD;
        };
    }
}
