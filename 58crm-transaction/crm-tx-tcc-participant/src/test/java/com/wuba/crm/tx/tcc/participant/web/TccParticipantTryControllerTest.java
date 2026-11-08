package com.wuba.crm.tx.tcc.participant.web;

import com.wuba.crm.tx.tcc.api.TccTryResult;
import com.wuba.crm.tx.tcc.participant.barrier.TccParticipantService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证通用 /tcc/try controller 的响应映射。
 */
class TccParticipantTryControllerTest {

    @Test
    void tryAction_mapsSuccess() {
        TccParticipantService service = mock(TccParticipantService.class);
        when(service.tryAction(any())).thenReturn(TccTryResult.SUCCESS);
        TccParticipantTryController controller = new TccParticipantTryController(service);

        TccTryResponse response = controller.tryAction(
                new TccTryRequest(1L, 2L, "reserveQuota", "{}"));

        assertTrue(response.success());
        assertEquals(TccTryResult.SUCCESS.name(), response.message());
    }
}
