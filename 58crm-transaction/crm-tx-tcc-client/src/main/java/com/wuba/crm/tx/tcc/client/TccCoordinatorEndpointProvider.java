package com.wuba.crm.tx.tcc.client;

/**
 * Coordinator gRPC 地址提供者。
 *
 * <p>实现可以是直连地址、Nacos discovery 或测试桩。</p>
 */
@FunctionalInterface
public interface TccCoordinatorEndpointProvider {

    /**
     * 返回 gRPC target，例如 {@code 127.0.0.1:9090}。
     *
     * @return gRPC target
     */
    String target();
}
