package com.wuba.crm.tx.tcc.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证全局状态迁移矩阵和终态判断。
 */
class GlobalStatusTest {

    @Test
    void trying_canOnlyMoveToConfirmingOrCancelling() {
        assertTrue(GlobalStatus.TRYING.canTransitionTo(GlobalStatus.CONFIRMING));
        assertTrue(GlobalStatus.TRYING.canTransitionTo(GlobalStatus.CANCELLING));
        assertFalse(GlobalStatus.TRYING.canTransitionTo(GlobalStatus.CONFIRMED));
        assertFalse(GlobalStatus.TRYING.canTransitionTo(GlobalStatus.FAILED));
    }

    @Test
    void confirming_canOnlyMoveToConfirmedOrFailed() {
        assertTrue(GlobalStatus.CONFIRMING.canTransitionTo(GlobalStatus.CONFIRMED));
        assertTrue(GlobalStatus.CONFIRMING.canTransitionTo(GlobalStatus.FAILED));
        assertFalse(GlobalStatus.CONFIRMING.canTransitionTo(GlobalStatus.CANCELLING));
        assertFalse(GlobalStatus.CONFIRMING.canTransitionTo(GlobalStatus.CANCELLED));
    }

    @Test
    void cancelling_canOnlyMoveToCancelledOrFailed() {
        assertTrue(GlobalStatus.CANCELLING.canTransitionTo(GlobalStatus.CANCELLED));
        assertTrue(GlobalStatus.CANCELLING.canTransitionTo(GlobalStatus.FAILED));
        assertFalse(GlobalStatus.CANCELLING.canTransitionTo(GlobalStatus.CONFIRMING));
        assertFalse(GlobalStatus.CANCELLING.canTransitionTo(GlobalStatus.CONFIRMED));
    }

    @Test
    void terminalStatuses_haveNoOutgoingTransition() {
        assertTrue(GlobalStatus.CONFIRMED.isTerminal());
        assertTrue(GlobalStatus.CANCELLED.isTerminal());
        assertTrue(GlobalStatus.FAILED.isTerminal());
        assertFalse(GlobalStatus.TRYING.isTerminal());
        assertFalse(GlobalStatus.CONFIRMING.isTerminal());
        assertFalse(GlobalStatus.CANCELLING.isTerminal());
    }

    @Test
    void nullTarget_isRejected() {
        assertFalse(GlobalStatus.TRYING.canTransitionTo(null));
    }
}
