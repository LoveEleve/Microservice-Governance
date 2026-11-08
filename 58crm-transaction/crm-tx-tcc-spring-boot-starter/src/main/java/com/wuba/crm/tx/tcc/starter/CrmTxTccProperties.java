package com.wuba.crm.tx.tcc.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TCC Starter 配置。
 *
 * <p>client 和 participant 使用独立开关；业务可以只发起 TCC，
 * 也可以只作为参与方。</p>
 */
@ConfigurationProperties(prefix = "crm.tx.tcc")
public class CrmTxTccProperties {

    private Client client = new Client();
    private Participant participant = new Participant();

    /**
     * @return client 配置
     */
    public Client getClient() {
        return client;
    }

    /**
     * @param client client 配置
     */
    public void setClient(Client client) {
        this.client = client;
    }

    /**
     * @return participant 配置
     */
    public Participant getParticipant() {
        return participant;
    }

    /**
     * @param participant participant 配置
     */
    public void setParticipant(Participant participant) {
        this.participant = participant;
    }

    /**
     * TM 客户端配置。
     */
    public static class Client {

        private boolean enabled;
        private String coordinatorServiceName = "crm-tx-tcc-coordinator";
        private String coordinatorAddress;
        private long deadlineSeconds = 15;

        /** @return 是否启用客户端 */
        public boolean isEnabled() {
            return enabled;
        }

        /** @param enabled 是否启用 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** @return Coordinator 服务名 */
        public String getCoordinatorServiceName() {
            return coordinatorServiceName;
        }

        /** @param value 服务名 */
        public void setCoordinatorServiceName(String value) {
            this.coordinatorServiceName = value;
        }

        /** @return 直连地址，优先级低于 discovery */
        public String getCoordinatorAddress() {
            return coordinatorAddress;
        }

        /** @param value 直连地址 */
        public void setCoordinatorAddress(String value) {
            this.coordinatorAddress = value;
        }

        /** @return 调用超时秒数 */
        public long getDeadlineSeconds() {
            return deadlineSeconds;
        }

        /** @param value 超时秒数 */
        public void setDeadlineSeconds(long value) {
            this.deadlineSeconds = value;
        }
    }

    /**
     * RM 参与方配置。
     */
    public static class Participant {

        private boolean enabled;
        private boolean grpcEnabled = true;
        private int grpcPort = 9091;
        private boolean tryEndpointEnabled = true;

        /** @return 是否启用参与方 */
        public boolean isEnabled() {
            return enabled;
        }

        /** @param enabled 是否启用 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** @return 是否启动 callback gRPC server */
        public boolean isGrpcEnabled() {
            return grpcEnabled;
        }

        /** @param value 是否启动 */
        public void setGrpcEnabled(boolean value) {
            this.grpcEnabled = value;
        }

        /** @return callback gRPC 端口 */
        public int getGrpcPort() {
            return grpcPort;
        }

        /** @param value 端口 */
        public void setGrpcPort(int value) {
            this.grpcPort = value;
        }

        /** @return 是否启用通用 Try endpoint */
        public boolean isTryEndpointEnabled() {
            return tryEndpointEnabled;
        }

        /** @param value 是否启用 */
        public void setTryEndpointEnabled(boolean value) {
            this.tryEndpointEnabled = value;
        }
    }
}
