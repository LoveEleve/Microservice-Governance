package com.wuba.crm.tx.tcc.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchStatusTest {

    @Test
    void trying_canSucceedFailOrCancel() {
        assertTrue(BranchStatus.TRYING.canTransitionTo(BranchStatus.TRY_SUCCESS));
        assertTrue(BranchStatus.TRYING.canTransitionTo(BranchStatus.TRY_FAIL));
        assertTrue(BranchStatus.TRYING.canTransitionTo(BranchStatus.CANCELLING));
        assertFalse(BranchStatus.TRYING.canTransitionTo(BranchStatus.CONFIRMING));
        assertFalse(BranchStatus.TRYING.canTransitionTo(BranchStatus.CONFIRMED));
    }

    @Test
    void trySuccess_canConfirmOrCancel() {
        assertTrue(BranchStatus.TRY_SUCCESS.canTransitionTo(BranchStatus.CONFIRMING));
        assertTrue(BranchStatus.TRY_SUCCESS.canTransitionTo(BranchStatus.CANCELLING));
        assertFalse(BranchStatus.TRY_SUCCESS.canTransitionTo(BranchStatus.CONFIRMED));
    }

    @Test
    void tryFail_canOnlyCancel() {
        assertTrue(BranchStatus.TRY_FAIL.canTransitionTo(BranchStatus.CANCELLING));
        assertFalse(BranchStatus.TRY_FAIL.canTransitionTo(BranchStatus.CONFIRMING));
    }

    @Test
    void confirming_canConfirmOrDie() {
        assertTrue(BranchStatus.CONFIRMING.canTransitionTo(BranchStatus.CONFIRMED));
        assertTrue(BranchStatus.CONFIRMING.canTransitionTo(BranchStatus.DEAD));
        assertFalse(BranchStatus.CONFIRMING.canTransitionTo(BranchStatus.CANCELLING));
    }

    @Test
    void cancelling_canCancelSuspendOrDie() {
        assertTrue(BranchStatus.CANCELLING.canTransitionTo(BranchStatus.CANCELLED));
        assertTrue(BranchStatus.CANCELLING.canTransitionTo(BranchStatus.SUSPENDED));
        assertTrue(BranchStatus.CANCELLING.canTransitionTo(BranchStatus.DEAD));
        assertFalse(BranchStatus.CANCELLING.canTransitionTo(BranchStatus.CONFIRMED));
    }

    @Test
    void terminalStatuses_areTerminal() {
        assertTrue(BranchStatus.CONFIRMED.isTerminal());
        assertTrue(BranchStatus.CANCELLED.isTerminal());
        assertTrue(BranchStatus.SUSPENDED.isTerminal());
        assertTrue(BranchStatus.DEAD.isTerminal());
        assertFalse(BranchStatus.CONFIRMING.isTerminal());
    }

    @Test
    void cancelFinished_coversCancelledAndSuspended() {
        assertTrue(BranchStatus.CANCELLED.isCancelFinished());
        assertTrue(BranchStatus.SUSPENDED.isCancelFinished());
        assertFalse(BranchStatus.CONFIRMED.isCancelFinished());
        assertFalse(BranchStatus.CONFIRMING.isCancelFinished());
    }
}
