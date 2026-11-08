package com.wuba.crm.tx.tcc.participant.grpc;

import com.wuba.crm.tx.tcc.api.BusinessActionContext;
import com.wuba.crm.tx.tcc.api.TccException;
import com.wuba.crm.tx.tcc.api.TccPhaseResult;
import com.wuba.crm.tx.tcc.api.TccResultCode;
import com.wuba.crm.tx.tcc.api.grpc.CancelRequest;
import com.wuba.crm.tx.tcc.api.grpc.CancelResponse;
import com.wuba.crm.tx.tcc.api.grpc.ConfirmRequest;
import com.wuba.crm.tx.tcc.api.grpc.ConfirmResponse;
import com.wuba.crm.tx.tcc.api.grpc.PhaseResult;
import com.wuba.crm.tx.tcc.api.grpc.TccParticipantCallbackGrpc;
import com.wuba.crm.tx.tcc.participant.barrier.TccParticipantService;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * RM 侧 gRPC callback adapter。
 *
 * <p>只负责 proto/API 映射和错误分类；barrier 与业务动作在
 * {@link TccParticipantService} 的本地事务中执行。</p>
 */
@Component
public class TccParticipantCallbackGrpcService
        extends TccParticipantCallbackGrpc.TccParticipantCallbackImplBase {

    private static final Logger log =
            Logger.getLogger(TccParticipantCallbackGrpcService.class.getName());

    private final TccParticipantService participantService;

    /**
     * 创建 adapter。
     *
     * @param participantService participant 执行器
     */
    public TccParticipantCallbackGrpcService(TccParticipantService participantService) {
        if (participantService == null) {
            throw new IllegalArgumentException("participantService must not be null");
        }
        this.participantService = participantService;
    }

    /**
     * 执行 Confirm callback。
     *
     * @param request  proto 请求
     * @param observer 响应回调
     */
    @Override
    public void confirm(ConfirmRequest request, StreamObserver<ConfirmResponse> observer) {
        try {
            TccPhaseResult result = participantService.confirm(context(
                    request.getXid(), request.getBranchId(),
                    request.getActionName(), request.getApplicationData()));
            observer.onNext(ConfirmResponse.newBuilder()
                    .setResult(toProto(result))
                    .setMessage("")
                    .build());
            observer.onCompleted();
        } catch (TccException failure) {
            respondConfirm(observer, failure.getCode(), failure.getMessage());
        } catch (IllegalArgumentException invalid) {
            respondConfirm(observer, TccResultCode.TCC_INVALID_ARGUMENT, invalid.getMessage());
        } catch (RuntimeException unexpected) {
            log.log(Level.SEVERE, "TCC participant confirm failed", unexpected);
            respondConfirm(observer, TccResultCode.TCC_INTERNAL_ERROR, "internal error");
        }
    }

    /**
     * 执行 Cancel callback。
     *
     * @param request  proto 请求
     * @param observer 响应回调
     */
    @Override
    public void cancel(CancelRequest request, StreamObserver<CancelResponse> observer) {
        try {
            TccPhaseResult result = participantService.cancel(context(
                    request.getXid(), request.getBranchId(),
                    request.getActionName(), request.getApplicationData()));
            observer.onNext(CancelResponse.newBuilder()
                    .setResult(toProto(result))
                    .setMessage("")
                    .build());
            observer.onCompleted();
        } catch (TccException failure) {
            respondCancel(observer, failure.getCode(), failure.getMessage());
        } catch (IllegalArgumentException invalid) {
            respondCancel(observer, TccResultCode.TCC_INVALID_ARGUMENT, invalid.getMessage());
        } catch (RuntimeException unexpected) {
            log.log(Level.SEVERE, "TCC participant cancel failed", unexpected);
            respondCancel(observer, TccResultCode.TCC_INTERNAL_ERROR, "internal error");
        }
    }

    private BusinessActionContext context(
            long xid, long branchId, String actionName, String applicationData) {
        return new BusinessActionContext(xid, branchId, actionName, applicationData);
    }

    private void respondConfirm(
            StreamObserver<ConfirmResponse> observer,
            TccResultCode code,
            String message) {
        observer.onNext(ConfirmResponse.newBuilder()
                .setResult(toProto(code))
                .setMessage(message == null ? "" : message)
                .build());
        observer.onCompleted();
    }

    private void respondCancel(
            StreamObserver<CancelResponse> observer,
            TccResultCode code,
            String message) {
        observer.onNext(CancelResponse.newBuilder()
                .setResult(toProto(code))
                .setMessage(message == null ? "" : message)
                .build());
        observer.onCompleted();
    }

    private PhaseResult toProto(TccPhaseResult result) {
        return switch (result) {
            case SUCCESS -> PhaseResult.PHASE_SUCCESS;
            case RETRYABLE -> PhaseResult.PHASE_RETRYABLE;
            case PERMANENT_FAILURE -> PhaseResult.PHASE_PERMANENT_FAILURE;
        };
    }

    private PhaseResult toProto(TccResultCode code) {
        return switch (code) {
            case TCC_BUSY, TCC_INTERNAL_ERROR, TCC_CALLBACK_FAILED -> PhaseResult.PHASE_RETRYABLE;
            default -> PhaseResult.PHASE_PERMANENT_FAILURE;
        };
    }
}
