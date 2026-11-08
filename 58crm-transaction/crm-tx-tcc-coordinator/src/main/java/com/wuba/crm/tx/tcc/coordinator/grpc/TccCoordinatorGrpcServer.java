package com.wuba.crm.tx.tcc.coordinator.grpc;

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
 * Coordinator gRPC server 生命周期。
 *
 * <p>业务逻辑不在 server 中；server 只负责监听端口、注册 adapter 和优雅关闭。</p>
 */
@Component
public class TccCoordinatorGrpcServer implements InitializingBean, DisposableBean {

    private static final Logger log = Logger.getLogger(TccCoordinatorGrpcServer.class.getName());

    private final TccCoordinatorGrpcService service;
    private final int port;
    private Server server;

    /**
     * 创建 server。
     *
     * @param service Coordinator gRPC adapter
     * @param port    gRPC 端口
     */
    public TccCoordinatorGrpcServer(
            TccCoordinatorGrpcService service,
            @Value("${crm.tx.tcc.coordinator.grpc-port:9090}") int port) {
        this.service = service;
        this.port = port;
    }

    /**
     * 启动 gRPC server。
     *
     * @throws IOException 端口绑定失败
     */
    @Override
    public void afterPropertiesSet() throws IOException {
        server = NettyServerBuilder.forPort(port)
                .addService(service)
                .build()
                .start();
        log.info("TCC coordinator gRPC server started on port " + port);
    }

    /**
     * 优雅关闭 gRPC server。
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
        log.info("TCC coordinator gRPC server stopped");
    }
}
