package com.wuba.crm.tx.tcc.coordinator.grpc;

import com.wuba.crm.tx.tcc.coordinator.service.TccCoordinatorService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

/**
 * 验证 gRPC server 能绑定端口并优雅关闭。
 */
class TccCoordinatorGrpcServerTest {

    @Test
    void serverStartsAndStopsOnEphemeralPort() throws Exception {
        TccCoordinatorGrpcService service = new TccCoordinatorGrpcService(
                mock(TccCoordinatorService.class),
                mock(com.wuba.crm.tx.tcc.coordinator.service.TccPhase2Coordinator.class));
        TccCoordinatorGrpcServer server = new TccCoordinatorGrpcServer(service, 0);

        server.afterPropertiesSet();
        server.destroy();
    }
}
