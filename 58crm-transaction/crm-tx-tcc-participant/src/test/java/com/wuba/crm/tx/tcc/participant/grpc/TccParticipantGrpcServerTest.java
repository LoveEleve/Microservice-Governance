package com.wuba.crm.tx.tcc.participant.grpc;

import com.wuba.crm.tx.tcc.participant.barrier.TccParticipantService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

/**
 * 验证 Participant gRPC server 能绑定端口并优雅关闭。
 */
class TccParticipantGrpcServerTest {

    @Test
    void serverStartsAndStopsOnEphemeralPort() throws Exception {
        TccParticipantCallbackGrpcService service =
                new TccParticipantCallbackGrpcService(mock(TccParticipantService.class));
        TccParticipantGrpcServer server = new TccParticipantGrpcServer(service, 0);

        server.afterPropertiesSet();
        server.destroy();
    }
}
