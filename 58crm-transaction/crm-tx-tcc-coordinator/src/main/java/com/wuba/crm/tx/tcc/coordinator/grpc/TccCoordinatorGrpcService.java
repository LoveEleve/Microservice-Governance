package com.wuba.crm.tx.tcc.coordinator.grpc;

import com.wuba.crm.tx.tcc.api.BranchStatus;
import com.wuba.crm.tx.tcc.api.GlobalStatus;
import com.wuba.crm.tx.tcc.api.TccBeginRequest;
import com.wuba.crm.tx.tcc.api.TccBeginResult;
import com.wuba.crm.tx.tcc.api.TccBranchRegisterRequest;
import com.wuba.crm.tx.tcc.api.TccBranchRegisterResult;
import com.wuba.crm.tx.tcc.api.TccBranchReportRequest;
import com.wuba.crm.tx.tcc.api.TccException;
import com.wuba.crm.tx.tcc.api.TccResultCode;
import com.wuba.crm.tx.tcc.api.grpc.BeginRequest;
import com.wuba.crm.tx.tcc.api.grpc.BeginResponse;
import com.wuba.crm.tx.tcc.api.grpc.RegisterBranchRequest;
import com.wuba.crm.tx.tcc.api.grpc.RegisterBranchResponse;
import com.wuba.crm.tx.tcc.api.grpc.ReportBranchRequest;
import com.wuba.crm.tx.tcc.api.grpc.ReportBranchResponse;
import com.wuba.crm.tx.tcc.api.grpc.ResultCode;
import com.wuba.crm.tx.tcc.api.grpc.TccCoordinatorGrpc;
import com.wuba.crm.tx.tcc.coordinator.service.TccCoordinatorService;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * gRPC 控制面 adapter。
 *
 * <p>只做协议编解码和错误码映射；事务状态机在
 * {@link TccCoordinatorService}，不在这里写业务逻辑。</p>
 */
@Component
public class TccCoordinatorGrpcService extends TccCoordinatorGrpc.TccCoordinatorImplBase {

    private static final Logger log = Logger.getLogger(TccCoordinatorGrpcService.class.getName());

    private final TccCoordinatorService service;

    /**
     * 创建 adapter。
     *
     * @param service Coordinator 核心服务
     */
    public TccCoordinatorGrpcService(TccCoordinatorService service) {
        if (service == null) {
            throw new IllegalArgumentException("service must not be null");
        }
        this.service = service;
    }

    /**
     * 开启全局事务。
     *
     * @param request  proto 请求
     * @param observer 响应回调
     */
    @Override
    public void begin(BeginRequest request, StreamObserver<BeginResponse> observer) {
        try {
            TccBeginResult result = service.begin(new TccBeginRequest(
                    request.getRequestId(),
                    request.getBizCode(),
                    request.getBizId(),
                    request.getTryTimeoutSeconds()));
            observer.onNext(BeginResponse.newBuilder()
                    .setCode(ResultCode.OK)
                    .setMessage("")
                    .setXid(result.xid())
                    .setStatus(toProto(result.status()))
                    .build());
            observer.onCompleted();
        } catch (TccException failure) {
            respondBegin(observer, failure.getCode(), failure.getMessage());
        } catch (IllegalArgumentException invalid) {
            respondBegin(observer, TccResultCode.TCC_INVALID_ARGUMENT, invalid.getMessage());
        } catch (RuntimeException unexpected) {
            log.log(Level.SEVERE, "TCC begin failed", unexpected);
            respondBegin(observer, TccResultCode.TCC_INTERNAL_ERROR, "internal error");
        }
    }

    /**
     * 注册分支。
     *
     * @param request  proto 请求
     * @param observer 响应回调
     */
    @Override
    public void registerBranch(RegisterBranchRequest request,
                               StreamObserver<RegisterBranchResponse> observer) {
        try {
            TccBranchRegisterResult result = service.registerBranch(
                    new TccBranchRegisterRequest(
                            request.getXid(),
                            request.getResourceKey(),
                            request.getActionName(),
                            request.getCallbackEndpoint(),
                            request.getApplicationData()));
            observer.onNext(RegisterBranchResponse.newBuilder()
                    .setCode(ResultCode.OK)
                    .setMessage("")
                    .setBranchId(result.branchId())
                    .setStatus(toProto(result.status()))
                    .build());
            observer.onCompleted();
        } catch (TccException failure) {
            respondRegister(observer, failure.getCode(), failure.getMessage());
        } catch (IllegalArgumentException invalid) {
            respondRegister(observer, TccResultCode.TCC_INVALID_ARGUMENT, invalid.getMessage());
        } catch (RuntimeException unexpected) {
            log.log(Level.SEVERE, "TCC registerBranch failed", unexpected);
            respondRegister(observer, TccResultCode.TCC_INTERNAL_ERROR, "internal error");
        }
    }

    /**
     * 上报 Try 结果。
     *
     * @param request  proto 请求
     * @param observer 响应回调
     */
    @Override
    public void reportBranch(ReportBranchRequest request,
                             StreamObserver<ReportBranchResponse> observer) {
        try {
            service.reportBranch(new TccBranchReportRequest(
                    request.getXid(),
                    request.getBranchId(),
                    toBranchStatus(request.getStatus()),
                    request.getMessage()));
            observer.onNext(ReportBranchResponse.newBuilder()
                    .setCode(ResultCode.OK)
                    .setMessage("")
                    .build());
            observer.onCompleted();
        } catch (TccException failure) {
            respondReport(observer, failure.getCode(), failure.getMessage());
        } catch (IllegalArgumentException invalid) {
            respondReport(observer, TccResultCode.TCC_INVALID_ARGUMENT, invalid.getMessage());
        } catch (RuntimeException unexpected) {
            log.log(Level.SEVERE, "TCC reportBranch failed", unexpected);
            respondReport(observer, TccResultCode.TCC_INTERNAL_ERROR, "internal error");
        }
    }

    private void respondBegin(
            StreamObserver<BeginResponse> observer, TccResultCode code, String message) {
        observer.onNext(BeginResponse.newBuilder()
                .setCode(toProto(code))
                .setMessage(safe(message))
                .build());
        observer.onCompleted();
    }

    private void respondRegister(
            StreamObserver<RegisterBranchResponse> observer, TccResultCode code, String message) {
        observer.onNext(RegisterBranchResponse.newBuilder()
                .setCode(toProto(code))
                .setMessage(safe(message))
                .build());
        observer.onCompleted();
    }

    private void respondReport(
            StreamObserver<ReportBranchResponse> observer, TccResultCode code, String message) {
        observer.onNext(ReportBranchResponse.newBuilder()
                .setCode(toProto(code))
                .setMessage(safe(message))
                .build());
        observer.onCompleted();
    }

    private String safe(String message) {
        return message == null ? "" : message;
    }

    private com.wuba.crm.tx.tcc.api.grpc.GlobalStatus toProto(GlobalStatus status) {
        return switch (status) {
            case TRYING -> com.wuba.crm.tx.tcc.api.grpc.GlobalStatus.TRYING;
            case CONFIRMING -> com.wuba.crm.tx.tcc.api.grpc.GlobalStatus.CONFIRMING;
            case CONFIRMED -> com.wuba.crm.tx.tcc.api.grpc.GlobalStatus.CONFIRMED;
            case CANCELLING -> com.wuba.crm.tx.tcc.api.grpc.GlobalStatus.CANCELLING;
            case CANCELLED -> com.wuba.crm.tx.tcc.api.grpc.GlobalStatus.CANCELLED;
            case FAILED -> com.wuba.crm.tx.tcc.api.grpc.GlobalStatus.FAILED;
        };
    }

    private com.wuba.crm.tx.tcc.api.grpc.BranchStatus toProto(BranchStatus status) {
        return switch (status) {
            case TRYING -> com.wuba.crm.tx.tcc.api.grpc.BranchStatus.BRANCH_TRYING;
            case TRY_SUCCESS -> com.wuba.crm.tx.tcc.api.grpc.BranchStatus.BRANCH_TRY_SUCCESS;
            case TRY_FAIL -> com.wuba.crm.tx.tcc.api.grpc.BranchStatus.BRANCH_TRY_FAIL;
            case CONFIRMING -> com.wuba.crm.tx.tcc.api.grpc.BranchStatus.BRANCH_CONFIRMING;
            case CONFIRMED -> com.wuba.crm.tx.tcc.api.grpc.BranchStatus.BRANCH_CONFIRMED;
            case CANCELLING -> com.wuba.crm.tx.tcc.api.grpc.BranchStatus.BRANCH_CANCELLING;
            case CANCELLED -> com.wuba.crm.tx.tcc.api.grpc.BranchStatus.BRANCH_CANCELLED;
            case SUSPENDED -> com.wuba.crm.tx.tcc.api.grpc.BranchStatus.BRANCH_SUSPENDED;
            case DEAD -> com.wuba.crm.tx.tcc.api.grpc.BranchStatus.BRANCH_DEAD;
        };
    }

    private BranchStatus toBranchStatus(com.wuba.crm.tx.tcc.api.grpc.BranchStatus status) {
        return switch (status) {
            case BRANCH_TRY_SUCCESS -> BranchStatus.TRY_SUCCESS;
            case BRANCH_TRY_FAIL -> BranchStatus.TRY_FAIL;
            default -> throw new IllegalArgumentException(
                    "report status must be TRY_SUCCESS or TRY_FAIL: " + status);
        };
    }

    private ResultCode toProto(TccResultCode code) {
        return switch (code) {
            case OK -> ResultCode.OK;
            case TCC_INVALID_ARGUMENT -> ResultCode.TCC_INVALID_ARGUMENT;
            case TCC_NOT_READY -> ResultCode.TCC_NOT_READY;
            case TCC_INVALID_STATE -> ResultCode.TCC_INVALID_STATE;
            case TCC_BUSY -> ResultCode.TCC_BUSY;
            case TCC_NOT_FOUND -> ResultCode.TCC_NOT_FOUND;
            case TCC_DUPLICATE -> ResultCode.TCC_DUPLICATE;
            case TCC_RETRY_EXHAUSTED -> ResultCode.TCC_RETRY_EXHAUSTED;
            case TCC_CALLBACK_FAILED -> ResultCode.TCC_CALLBACK_FAILED;
            case TCC_INTERNAL_ERROR -> ResultCode.TCC_INTERNAL_ERROR;
        };
    }
}
