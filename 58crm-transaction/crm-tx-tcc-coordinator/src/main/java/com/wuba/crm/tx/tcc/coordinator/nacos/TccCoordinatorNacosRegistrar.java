package com.wuba.crm.tx.tcc.coordinator.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Coordinator gRPC 实例的 Nacos 注册器。
 *
 * <p>Spring Cloud Nacos 默认按 Web 端口注册；Coordinator 没有 Web 容器，
 * 因此这里按 gRPC 端口和 {@code protocol=grpc} 元数据显式注册。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "crm.tx.tcc.coordinator.nacos",
        name = "enabled",
        havingValue = "true")
public class TccCoordinatorNacosRegistrar implements DisposableBean {

    private static final Logger log =
            Logger.getLogger(TccCoordinatorNacosRegistrar.class.getName());

    private final NamingService namingService;
    private final String serviceName;
    private final String group;
    private final String cluster;
    private final String ip;
    private final int grpcPort;

    /**
     * 创建注册器。
     *
     * @param serverAddr  Nacos server 地址
     * @param serviceName Nacos service name
     * @param group       分组
     * @param cluster     集群
     * @param ip          注册 IP，空则取本机地址
     * @param grpcPort    gRPC 端口
     * @throws Exception 初始化失败
     */
    public TccCoordinatorNacosRegistrar(
            @Value("${spring.cloud.nacos.discovery.server-addr:127.0.0.1:8848}") String serverAddr,
            @Value("${spring.application.name:crm-tx-tcc-coordinator}") String serviceName,
            @Value("${crm.tx.tcc.coordinator.nacos.group:DEFAULT_GROUP}") String group,
            @Value("${crm.tx.tcc.coordinator.nacos.cluster:DEFAULT}") String cluster,
            @Value("${crm.tx.tcc.coordinator.nacos.ip:}") String ip,
            @Value("${crm.tx.tcc.coordinator.grpc-port:9090}") int grpcPort) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverAddr);
        this.namingService = NacosFactory.createNamingService(properties);
        this.serviceName = serviceName;
        this.group = group;
        this.cluster = cluster;
        this.ip = ip;
        this.grpcPort = grpcPort;
    }

    /**
     * 应用就绪后注册 gRPC 实例。
     *
     * @throws Exception 注册失败
     */
    @EventListener(ApplicationReadyEvent.class)
    public void register() throws Exception {
        Instance instance = new Instance();
        instance.setIp(ip == null || ip.isBlank()
                ? InetAddress.getLocalHost().getHostAddress() : ip);
        instance.setPort(grpcPort);
        instance.setClusterName(cluster);
        instance.setServiceName(serviceName);
        instance.setMetadata(Map.of("protocol", "grpc"));
        namingService.registerInstance(serviceName, group, instance);
        log.info("TCC coordinator registered to Nacos: "
                + serviceName + " " + instance.getIp() + ":" + grpcPort);
    }

    /**
     * 关闭前注销实例。
     */
    @Override
    public void destroy() {
        try {
            namingService.shutDown();
        } catch (Exception failure) {
            log.warning("TCC coordinator Nacos shutdown failed: " + failure.getMessage());
        }
    }
}
