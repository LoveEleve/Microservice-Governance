package com.wuba.crm.tx.tcc.client;

import com.wuba.crm.tx.tcc.api.BranchStatus;
import com.wuba.crm.tx.tcc.api.GlobalStatus;
import com.wuba.crm.tx.tcc.protocol.model.TccBeginRequest;
import com.wuba.crm.tx.tcc.protocol.model.TccBeginResult;
import com.wuba.crm.tx.tcc.api.TccBranchSpec;
import com.wuba.crm.tx.tcc.protocol.model.TccBranchRegisterRequest;
import com.wuba.crm.tx.tcc.protocol.model.TccBranchRegisterResult;
import com.wuba.crm.tx.tcc.protocol.model.TccBranchReportRequest;
import com.wuba.crm.tx.tcc.protocol.model.TccCommitResult;
import com.wuba.crm.tx.tcc.api.TccRequest;
import com.wuba.crm.tx.tcc.protocol.model.TccRollbackResult;
import com.wuba.crm.tx.tcc.api.TccTryResult;
import com.wuba.crm.tx.tcc.client.internal.TccCoordinatorOperations;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证公共模板的调用顺序、Try 失败回滚和结果模型。
 */
class DefaultTccTransactionTemplateTest {

    @Test
    void execute_successCallsBeginRegisterReportCommit() {
        FakeCoordinator coordinator = new FakeCoordinator();
        List<String> calls = new ArrayList<>();
        DefaultTccTransactionTemplate template = new DefaultTccTransactionTemplate(coordinator);

        var result = template.execute(TccRequest.builder()
                .requestId("req-1")
                .bizCode("lead")
                .bizId("1")
                .branch(branch("quota:1", () -> {
                    calls.add("try:1");
                    return TccTryResult.SUCCESS;
                }))
                .branch(branch("quota:2", () -> {
                    calls.add("try:2");
                    return TccTryResult.SUCCESS;
                }))
                .build());

        assertEquals(GlobalStatus.CONFIRMED, result.status());
        assertTrue(result.isConfirmed());
        assertEquals(List.of(
                "begin",
                "register:quota:1",
                "register:quota:2",
                "report:1:TRY_SUCCESS",
                "report:2:TRY_SUCCESS",
                "commit"), coordinator.calls);
        assertEquals(List.of("try:1", "try:2"), calls);
    }

    @Test
    void execute_tryFailureRollsBackAndSkipsCommit() {
        FakeCoordinator coordinator = new FakeCoordinator();
        DefaultTccTransactionTemplate template = new DefaultTccTransactionTemplate(coordinator);

        var result = template.execute(TccRequest.builder()
                .requestId("req-1")
                .bizCode("lead")
                .bizId("1")
                .branch(branch("quota:1", () -> TccTryResult.SUCCESS))
                .branch(branch("quota:2", () -> TccTryResult.FAILURE))
                .build());

        assertEquals(GlobalStatus.CANCELLED, result.status());
        assertEquals(BranchStatus.TRY_SUCCESS, result.branches().get(0).status());
        assertEquals(BranchStatus.TRY_FAIL, result.branches().get(1).status());
        assertTrue(coordinator.calls.contains("rollback"));
        assertFalse(coordinator.calls.contains("commit"));
    }

    @Test
    void execute_tryExceptionRollsBack() {
        FakeCoordinator coordinator = new FakeCoordinator();
        DefaultTccTransactionTemplate template = new DefaultTccTransactionTemplate(coordinator);

        var result = template.execute(TccRequest.builder()
                .requestId("req-1")
                .bizCode("lead")
                .bizId("1")
                .branch(branch("quota:1", () -> {
                    throw new IllegalStateException("quota timeout");
                }))
                .build());

        assertEquals(GlobalStatus.CANCELLED, result.status());
        assertTrue(result.message().contains("quota timeout"));
        assertTrue(coordinator.calls.contains("rollback"));
    }

    @Test
    void execute_registerFailureRollsBackAndThrows() {
        FakeCoordinator coordinator = new FakeCoordinator();
        coordinator.failRegister = true;
        DefaultTccTransactionTemplate template = new DefaultTccTransactionTemplate(coordinator);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> template.execute(TccRequest.builder()
                        .requestId("req-1")
                        .bizCode("lead")
                        .bizId("1")
                        .branch(branch("quota:1", () -> TccTryResult.SUCCESS))
                        .build()));

        assertEquals("register failed", failure.getMessage());
        assertTrue(coordinator.calls.contains("rollback"));
    }

    @Test
    void execute_confirmingStatusIsReturnedAsIs() {
        FakeCoordinator coordinator = new FakeCoordinator();
        coordinator.commitStatus = GlobalStatus.CONFIRMING;
        DefaultTccTransactionTemplate template = new DefaultTccTransactionTemplate(coordinator);

        var result = template.execute(TccRequest.builder()
                .requestId("req-1")
                .bizCode("lead")
                .bizId("1")
                .branch(branch("quota:1", () -> TccTryResult.SUCCESS))
                .build());

        assertEquals(GlobalStatus.CONFIRMING, result.status());
        assertFalse(result.isConfirmed());
    }

    @Test
    void request_rejectsDuplicateResourceKeys() {
        TccBranchSpec first = branch("quota:1", () -> TccTryResult.SUCCESS);
        TccBranchSpec second = branch("quota:1", () -> TccTryResult.SUCCESS);

        assertThrows(IllegalArgumentException.class,
                () -> TccRequest.builder()
                        .requestId("req-1")
                        .bizCode("lead")
                        .bizId("1")
                        .branch(first)
                        .branch(second)
                        .build());
    }

    @Test
    void request_rejectsEmptyBranches() {
        assertThrows(IllegalArgumentException.class,
                () -> TccRequest.builder()
                        .requestId("req-1")
                        .bizCode("lead")
                        .bizId("1")
                        .build());
    }

    private TccBranchSpec branch(String resourceKey, TccTryCallback callback) {
        return TccBranchSpec.builder()
                .resourceKey(resourceKey)
                .actionName("reserveQuota")
                .callbackEndpoint("127.0.0.1:9092")
                .applicationData("{}")
                .tryAction(context -> callback.run())
                .build();
    }

    @FunctionalInterface
    private interface TccTryCallback {
        TccTryResult run();
    }

    /**
     * 记录协议调用的假 Coordinator。
     */
    private static final class FakeCoordinator implements TccCoordinatorOperations {

        private final List<String> calls = new ArrayList<>();
        private long nextBranchId = 1;
        private boolean failRegister;
        private GlobalStatus commitStatus = GlobalStatus.CONFIRMED;

        @Override
        public TccBeginResult begin(TccBeginRequest request) {
            calls.add("begin");
            return new TccBeginResult(100L, GlobalStatus.TRYING);
        }

        @Override
        public TccBranchRegisterResult registerBranch(TccBranchRegisterRequest request) {
            if (failRegister) {
                throw new IllegalStateException("register failed");
            }
            calls.add("register:" + request.resourceKey());
            return new TccBranchRegisterResult(nextBranchId++, BranchStatus.TRYING);
        }

        @Override
        public void reportBranch(TccBranchReportRequest request) {
            calls.add("report:" + request.branchId() + ":" + request.status());
        }

        @Override
        public TccCommitResult commit(long xid) {
            calls.add("commit");
            return new TccCommitResult(commitStatus);
        }

        @Override
        public TccRollbackResult rollback(long xid) {
            calls.add("rollback");
            return new TccRollbackResult(GlobalStatus.CANCELLED);
        }
    }
}
