package com.wuba.crm.tx.tcc.coordinator.grpc;

import com.wuba.crm.tx.tcc.api.BranchStatus;
import com.wuba.crm.tx.tcc.api.GlobalStatus;
import com.wuba.crm.tx.tcc.api.TccBeginResult;
import com.wuba.crm.tx.tcc.api.TccBranchRegisterResult;
import com.wuba.crm.tx.tcc.api.TccBranchInfo;
import com.wuba.crm.tx.tcc.api.TccCommitResult;
import com.wuba.crm.tx.tcc.api.TccException;
import com.wuba.crm.tx.tcc.api.TccResultCode;
import com.wuba.crm.tx.tcc.api.grpc.BeginRequest;
import com.wuba.crm.tx.tcc.api.grpc.BeginResponse;
import com.wuba.crm.tx.tcc.api.grpc.CommitRequest;
import com.wuba.crm.tx.tcc.api.grpc.CommitResponse;
import com.wuba.crm.tx.tcc.api.grpc.QueryRequest;
import com.wuba.crm.tx.tcc.api.grpc.QueryResponse;
import com.wuba.crm.tx.tcc.api.grpc.RegisterBranchRequest;
import com.wuba.crm.tx.tcc.api.grpc.RegisterBranchResponse;
import com.wuba.crm.tx.tcc.api.grpc.ReportBranchRequest;
import com.wuba.crm.tx.tcc.api.grpc.ReportBranchResponse;
import com.wuba.crm.tx.tcc.api.grpc.ResultCode;
import com.wuba.crm.tx.tcc.api.grpc.RollbackRequest;
import com.wuba.crm.tx.tcc.api.grpc.RollbackResponse;
import com.wuba.crm.tx.tcc.api.TccQueryResult;
import com.wuba.crm.tx.tcc.api.TccRollbackResult;
import com.wuba.crm.tx.tcc.coordinator.service.TccCoordinatorService;
import com.wuba.crm.tx.tcc.coordinator.service.TccPhase2Coordinator;
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
 * 验证 proto 与 API 的映射、错误码映射，不启动真实 gRPC 网络。
 */
class TccCoordinatorGrpcServiceTest {

    private TccCoordinatorService service;
    private TccPhase2Coordinator phase2Coordinator;
    private TccCoordinatorGrpcService adapter;

    @BeforeEach
    void setUp() {
        service = mock(TccCoordinatorService.class);
        phase2Coordinator = mock(TccPhase2Coordinator.class);
        adapter = new TccCoordinatorGrpcService(service, phase2Coordinator);
    }

    @Test
    void begin_mapsSuccessResponse() {
        when(service.begin(any())).thenReturn(new TccBeginResult(7L, GlobalStatus.TRYING));
        RecordingObserver<BeginResponse> observer = new RecordingObserver<>();

        adapter.begin(BeginRequest.newBuilder()
                .setRequestId("req-1")
                .setBizCode("lead")
                .setBizId("L-1")
                .setTryTimeoutSeconds(30)
                .build(), observer);

        assertTrue(observer.completed);
        BeginResponse response = observer.values.get(0);
        assertEquals(ResultCode.OK, response.getCode());
        assertEquals(7L, response.getXid());
        assertEquals(com.wuba.crm.tx.tcc.api.grpc.GlobalStatus.TRYING, response.getStatus());
    }

    @Test
    void begin_mapsTccExceptionCode() {
        when(service.begin(any())).thenThrow(
                new TccException(TccResultCode.TCC_INVALID_STATE, "bad state"));
        RecordingObserver<BeginResponse> observer = new RecordingObserver<>();

        adapter.begin(BeginRequest.newBuilder()
                .setRequestId("req-1")
                .setBizCode("lead")
                .setBizId("L-1")
                .setTryTimeoutSeconds(30)
                .build(), observer);

        assertEquals(ResultCode.TCC_INVALID_STATE, observer.values.get(0).getCode());
        assertEquals("bad state", observer.values.get(0).getMessage());
    }

    @Test
    void begin_mapsValidationFailureToInvalidArgument() {
        RecordingObserver<BeginResponse> observer = new RecordingObserver<>();

        adapter.begin(BeginRequest.newBuilder()
                .setRequestId(" ")
                .setBizCode("lead")
                .setBizId("L-1")
                .setTryTimeoutSeconds(30)
                .build(), observer);

        assertEquals(ResultCode.TCC_INVALID_ARGUMENT, observer.values.get(0).getCode());
    }

    @Test
    void registerBranch_mapsSuccessResponse() {
        when(service.registerBranch(any())).thenReturn(
                new TccBranchRegisterResult(3L, BranchStatus.TRYING));
        RecordingObserver<RegisterBranchResponse> observer = new RecordingObserver<>();

        adapter.registerBranch(RegisterBranchRequest.newBuilder()
                .setXid(7L)
                .setResourceKey("lead:1")
                .setActionName("assignLead")
                .setCallbackEndpoint("http://localhost:9090/tcc")
                .setApplicationData("{}")
                .build(), observer);

        assertEquals(ResultCode.OK, observer.values.get(0).getCode());
        assertEquals(3L, observer.values.get(0).getBranchId());
        assertEquals(com.wuba.crm.tx.tcc.api.grpc.BranchStatus.BRANCH_TRYING,
                observer.values.get(0).getStatus());
    }

    @Test
    void reportBranch_mapsSuccessResponse() {
        RecordingObserver<ReportBranchResponse> observer = new RecordingObserver<>();

        adapter.reportBranch(ReportBranchRequest.newBuilder()
                .setXid(7L)
                .setBranchId(3L)
                .setStatus(com.wuba.crm.tx.tcc.api.grpc.BranchStatus.BRANCH_TRY_SUCCESS)
                .build(), observer);

        assertEquals(ResultCode.OK, observer.values.get(0).getCode());
    }

    @Test
    void commit_mapsSuccessResponse() {
        when(phase2Coordinator.commit(7L)).thenReturn(
                new TccCommitResult(GlobalStatus.CONFIRMED));
        RecordingObserver<CommitResponse> observer = new RecordingObserver<>();

        adapter.commit(CommitRequest.newBuilder().setXid(7L).build(), observer);

        assertEquals(ResultCode.OK, observer.values.get(0).getCode());
        assertEquals(com.wuba.crm.tx.tcc.api.grpc.GlobalStatus.CONFIRMED,
                observer.values.get(0).getStatus());
    }

    @Test
    void rollback_mapsSuccessResponse() {
        when(phase2Coordinator.rollback(7L)).thenReturn(
                new TccRollbackResult(GlobalStatus.CANCELLED));
        RecordingObserver<RollbackResponse> observer = new RecordingObserver<>();

        adapter.rollback(RollbackRequest.newBuilder().setXid(7L).build(), observer);

        assertEquals(ResultCode.OK, observer.values.get(0).getCode());
        assertEquals(com.wuba.crm.tx.tcc.api.grpc.GlobalStatus.CANCELLED,
                observer.values.get(0).getStatus());
    }

    @Test
    void query_mapsBranches() {
        when(phase2Coordinator.query(7L)).thenReturn(new TccQueryResult(
                GlobalStatus.CONFIRMING,
                List.of(new TccBranchInfo(3L, "lead:1", "assignLead",
                        BranchStatus.CONFIRMING, 0))));
        RecordingObserver<QueryResponse> observer = new RecordingObserver<>();

        adapter.query(QueryRequest.newBuilder().setXid(7L).build(), observer);

        assertEquals(ResultCode.OK, observer.values.get(0).getCode());
        assertEquals(1, observer.values.get(0).getBranchesCount());
        assertEquals(3L, observer.values.get(0).getBranches(0).getBranchId());
    }

    @Test
    void reportBranch_rejectsNonTryStatusAsInvalidArgument() {
        RecordingObserver<ReportBranchResponse> observer = new RecordingObserver<>();

        adapter.reportBranch(ReportBranchRequest.newBuilder()
                .setXid(7L)
                .setBranchId(3L)
                .setStatus(com.wuba.crm.tx.tcc.api.grpc.BranchStatus.BRANCH_CONFIRMING)
                .build(), observer);

        assertEquals(ResultCode.TCC_INVALID_ARGUMENT, observer.values.get(0).getCode());
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
