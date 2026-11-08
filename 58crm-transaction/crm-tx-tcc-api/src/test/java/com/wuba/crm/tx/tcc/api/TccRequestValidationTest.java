package com.wuba.crm.tx.tcc.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 TCC DTO 的必填、状态和 64KB 上下文校验。
 */
class TccRequestValidationTest {

    @Test
    void beginRequest_rejectsBlankRequestId() {
        assertThrows(IllegalArgumentException.class,
                () -> new TccBeginRequest(" ", "lead", "L-1", 30));
    }

    @Test
    void beginRequest_rejectsNonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class,
                () -> new TccBeginRequest("r-1", "lead", "L-1", 0));
    }

    @Test
    void registerRequest_accepts64KBApplicationData() {
        String data = "x".repeat(TccValidation.MAX_APPLICATION_DATA_BYTES);

        TccBranchRegisterRequest request = new TccBranchRegisterRequest(
                1L, "lead:1", "assignLead", "http://localhost:9090/tcc", data);

        assertEquals(data, request.applicationData());
    }

    @Test
    void registerRequest_rejectsApplicationDataOver64KB() {
        String data = "x".repeat(TccValidation.MAX_APPLICATION_DATA_BYTES + 1);

        assertThrows(IllegalArgumentException.class,
                () -> new TccBranchRegisterRequest(
                        1L, "lead:1", "assignLead", "http://localhost:9090/tcc", data));
    }

    @Test
    void reportRequest_rejectsNonTryStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> new TccBranchReportRequest(1L, 1L, BranchStatus.CONFIRMING, ""));
    }

    @Test
    void reportRequest_acceptsTrySuccess() {
        TccBranchReportRequest request =
                new TccBranchReportRequest(1L, 1L, BranchStatus.TRY_SUCCESS, null);

        assertEquals("", request.message());
    }

    @Test
    void queryResult_copiesBranchList() {
        TccBranchInfo branch = new TccBranchInfo(
                1L, "lead:1", "assignLead", BranchStatus.TRY_SUCCESS, 0);
        List<TccBranchInfo> source = new java.util.ArrayList<>(List.of(branch));
        TccQueryResult result = new TccQueryResult(GlobalStatus.TRYING, source);

        source.clear();

        assertEquals(List.of(branch), result.branches());
    }

    @Test
    void callbackResult_factoriesCarryPhaseResult() {
        assertEquals(TccPhaseResult.SUCCESS, TccCallbackResult.success().result());
        assertEquals(TccPhaseResult.RETRYABLE, TccCallbackResult.retryable("busy").result());
        assertEquals(TccPhaseResult.PERMANENT_FAILURE,
                TccCallbackResult.permanentFailure("bad state").result());
    }

    @Test
    void resultCode_marksOnlyTransientCodesRetryable() {
        assertEquals(true, TccResultCode.TCC_NOT_READY.isRetryable());
        assertEquals(true, TccResultCode.TCC_BUSY.isRetryable());
        assertEquals(true, TccResultCode.TCC_CALLBACK_FAILED.isRetryable());
        assertEquals(false, TccResultCode.TCC_INVALID_STATE.isRetryable());
        assertEquals(false, TccResultCode.TCC_RETRY_EXHAUSTED.isRetryable());
    }
}
