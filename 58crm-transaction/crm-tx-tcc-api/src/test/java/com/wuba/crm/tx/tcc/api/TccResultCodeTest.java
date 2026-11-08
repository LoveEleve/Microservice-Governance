package com.wuba.crm.tx.tcc.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TccResultCodeTest {

    @Test
    void onlyTransientCodesAreRetryable() {
        assertTrue(TccResultCode.TCC_NOT_READY.isRetryable());
        assertTrue(TccResultCode.TCC_BUSY.isRetryable());
        assertTrue(TccResultCode.TCC_CALLBACK_FAILED.isRetryable());
        assertFalse(TccResultCode.TCC_INVALID_STATE.isRetryable());
        assertFalse(TccResultCode.TCC_RETRY_EXHAUSTED.isRetryable());
    }
}
