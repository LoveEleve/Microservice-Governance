package com.wuba.crm.tx.tcc.participant.grpc;

import com.wuba.crm.tx.tcc.api.TccException;
import com.wuba.crm.tx.tcc.api.TccPhaseResult;
import com.wuba.crm.tx.tcc.api.TccResultCode;
import com.wuba.crm.tx.tcc.api.grpc.CancelRequest;
import com.wuba.crm.tx.tcc.api.grpc.CancelResponse;
import com.wuba.crm.tx.tcc.api.grpc.ConfirmRequest;
import com.wuba.crm.tx.tcc.api.grpc.ConfirmResponse;
import com.wuba.crm.tx.tcc.api.grpc.PhaseResult;
import com.wuba.crm.tx.tcc.participant.barrier.TccParticipantService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 Participant callback 的 proto/API 映射和错误分类。
 */
class TccParticipantCallbackGrpcServiceTest {

    private TccParticipantService service;
    private TccParticipantCallbackGrpcService adapter;

    @BeforeEach
    void setUp() {
        service = mock(TccParticipantService.class);
        adapter = new TccParticipantCallbackGrpcService(service);
    }

    @Test
    void confirm_mapsSuccess() {
        when(service.confirm(any())).thenReturn(TccPhaseResult.SUCCESS);
        RecordingObserver<ConfirmResponse> observer = new RecordingObserver<>();

        adapter.confirm(confirmRequest(), observer);

        assertTrue(observer.completed);
        assertEquals(PhaseResult.PHASE_SUCCESS, observer.values.get(0).getResult());
    }

    @Test
    void confirm_mapsBusyToRetryable() {
        when(service.confirm(any())).thenThrow(
                new TccException(TccResultCode.TCC_BUSY, "busy"));
        RecordingObserver<ConfirmResponse> observer = new RecordingObserver<>();

        adapter.confirm(confirmRequest(), observer);

        assertEquals(PhaseResult.PHASE_RETRYABLE, observer.values.get(0).getResult());
    }

    @Test
    void confirm_mapsInvalidStateToPermanentFailure() {
        when(service.confirm(any())).thenThrow(
                new TccException(TccResultCode.TCC_INVALID_STATE, "bad state"));
        RecordingObserver<ConfirmResponse> observer = new RecordingObserver<>();

        adapter.confirm(confirmRequest(), observer);

        assertEquals(PhaseResult.PHASE_PERMANENT_FAILURE,
                observer.values.get(0).getResult());
    }

    @Test
    void cancel_mapsSuccess() {
        when(service.cancel(any())).thenReturn(TccPhaseResult.SUCCESS);
        RecordingObserver<CancelResponse> observer = new RecordingObserver<>();

        adapter.cancel(cancelRequest(), observer);

        assertEquals(PhaseResult.PHASE_SUCCESS, observer.values.get(0).getResult());
    }

    @Test
    void invalidRequest_isPermanentFailure() {
        RecordingObserver<ConfirmResponse> observer = new RecordingObserver<>();

        adapter.confirm(ConfirmRequest.newBuilder()
                .setXid(0L)
                .setBranchId(1L)
                .setActionName("assignLead")
                .build(), observer);

        assertEquals(PhaseResult.PHASE_PERMANENT_FAILURE,
                observer.values.get(0).getResult());
    }

    private ConfirmRequest confirmRequest() {
        return ConfirmRequest.newBuilder()
                .setXid(1L)
                .setBranchId(2L)
                .setActionName("assignLead")
                .setApplicationData("{}")
                .build();
    }

    private CancelRequest cancelRequest() {
        return CancelRequest.newBuilder()
                .setXid(1L)
                .setBranchId(2L)
                .setActionName("assignLead")
                .setApplicationData("{}")
                .build();
    }

    private static final class RecordingObserver<T> implements StreamObserver<T> {

        private final List<T> values = new ArrayList<>();
        private boolean completed;

        @Override
        public void onNext(T value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable throwable) {
            throw new AssertionError(throwable);
        }

        @Override
        public void onCompleted() {
            completed = true;
        }
    }
}
