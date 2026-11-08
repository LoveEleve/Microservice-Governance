package com.wuba.crm.tx.tcc.participant.grpc;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * RM 侧 gRPC callback server 生命周期。
 *
 * <p>业务应用启动时监听 callback 端口，TC 通过 branch register 时保存的
 * endpoint 调用 Confirm/Cancel。</p>
 */
@Component
public class TccParticipantGrpcServer implements InitializingBean, DisposableBean {

    private static final Logger log = Logger.getLogger(TccParticipantGrpcServer.class.getName());

    private final TccParticipantCallbackGrpcService service;
    private final int port;
    private Server server;

    /**
     * 创建 server。
     *
     * @param service callback adapter
     * @param port    gRPC 端口
     */
    public TccParticipantGrpcServer(
            TccParticipantCallbackGrpcService service,
            @Value("${crm.tx.tcc.participant.grpc-port:9091}") int port) {
        this.service = service;
        this.port = port;
    }

    /**
     * 启动 callback server。
     *
     * @throws IOException 端口绑定失败
     */
    @Override
    public void afterPropertiesSet() throws IOException {
        server = NettyServerBuilder.forPort(port)
                .addService(service)
                .build()
                .start();
        log.info("TCC participant gRPC server started on port " + port);
    }

    /**
     * 优雅关闭 callback server。
     */
    @Override
    public void destroy() {
        if (server == null) {
            return;
        }
        server.shutdown();
        try {
            if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                server.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            server.shutdownNow();
        }
        log.info("TCC participant gRPC server stopped");
    }
}
