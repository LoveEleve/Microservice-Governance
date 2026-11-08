package com.wuba.crm.tx.tcc.coordinator.callback;

import com.wuba.crm.tx.tcc.api.TccCallbackResult;
import com.wuba.crm.tx.tcc.api.TccPhaseResult;
import com.wuba.crm.tx.tcc.coordinator.persistence.TccBranchEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 callbackEndpoint 到 gRPC target 的转换，不发起真实网络请求。
 */
class GrpcTccParticipantClientTest {

    @Test
    void hostPort_isUsedDirectly() {
        assertEquals("localhost:9090",
                GrpcTccParticipantClient.target("localhost:9090"));
    }

    @Test
    void httpUrl_usesHostAndPort() {
        assertEquals("localhost:9090",
                GrpcTccParticipantClient.target("http://localhost:9090/tcc"));
    }

    @Test
    void missingPort_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> GrpcTccParticipantClient.target("http://localhost/tcc"));
    }

    @Test
    void blankEndpoint_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> GrpcTccParticipantClient.target(" "));
    }

    @Test
    void invalidEndpoint_returnsPermanentFailure() {
        TccBranchEntity branch = new TccBranchEntity();
        branch.setXid(1L);
        branch.setBranchId(2L);
        branch.setActionName("assignLead");
        branch.setCallbackEndpoint(" ");

        TccCallbackResult result = new GrpcTccParticipantClient(1000).confirm(branch);

        assertEquals(TccPhaseResult.PERMANENT_FAILURE, result.result());
    }
}
