package com.wuba.crm.tx.tcc.coordinator.callback;

import com.wuba.crm.tx.tcc.api.TccCallbackResult;
import com.wuba.crm.tx.tcc.protocol.grpc.CancelRequest;
import com.wuba.crm.tx.tcc.protocol.grpc.CancelResponse;
import com.wuba.crm.tx.tcc.protocol.grpc.ConfirmRequest;
import com.wuba.crm.tx.tcc.protocol.grpc.ConfirmResponse;
import com.wuba.crm.tx.tcc.protocol.grpc.TccParticipantCallbackGrpc;
import com.wuba.crm.tx.tcc.coordinator.persistence.TccBranchEntity;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 基于 gRPC 的 participant callback 实现。
 *
 * <p>第一版按内网可信、明文 gRPC 处理；callbackEndpoint 支持
 * {@code host:port} 和 {@code http://host:port/path} 两种写法。
 * 生产化需要改为 TLS 和连接复用。</p>
 */
@Component
public class GrpcTccParticipantClient implements TccParticipantClient {

    private static final Logger log = Logger.getLogger(GrpcTccParticipantClient.class.getName());

    private final long timeoutMillis;

    /**
     * 创建 gRPC 回调客户端。
     *
     * @param timeoutMillis 单次回调超时毫秒数
     */
    public GrpcTccParticipantClient(
            @Value("${crm.tx.tcc.coordinator.callback-timeout-ms:3000}") long timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * 调用 Confirm。
     *
     * @param branch 分支快照
     * @return 回调结果
     */
    @Override
    public TccCallbackResult confirm(TccBranchEntity branch) {
        ManagedChannel channel = null;
        try {
            channel = channel(branch.getCallbackEndpoint());
            ConfirmResponse response = TccParticipantCallbackGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(timeoutMillis, TimeUnit.MILLISECONDS)
                    .confirm(ConfirmRequest.newBuilder()
                            .setXid(branch.getXid())
                            .setBranchId(branch.getBranchId())
                            .setActionName(branch.getActionName())
                            .setApplicationData(safe(branch.getApplicationData()))
                            .build());
            return callbackResult(response.getResult(), response.getMessage());
        } catch (IllegalArgumentException invalid) {
            log.log(Level.WARNING, "Invalid TCC callback endpoint", invalid);
            return TccCallbackResult.permanentFailure(invalid.getMessage());
        } catch (RuntimeException failure) {
            log.log(Level.WARNING, "TCC confirm callback failed for "
                    + branch.getXid() + "/" + branch.getBranchId(), failure);
            return TccCallbackResult.retryable(failure.getMessage());
        } finally {
            shutdown(channel);
        }
    }

    /**
     * 调用 Cancel。
     *
     * @param branch 分支快照
     * @return 回调结果
     */
    @Override
    public TccCallbackResult cancel(TccBranchEntity branch) {
        ManagedChannel channel = null;
        try {
            channel = channel(branch.getCallbackEndpoint());
            CancelResponse response = TccParticipantCallbackGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(timeoutMillis, TimeUnit.MILLISECONDS)
                    .cancel(CancelRequest.newBuilder()
                            .setXid(branch.getXid())
                            .setBranchId(branch.getBranchId())
                            .setActionName(branch.getActionName())
                            .setApplicationData(safe(branch.getApplicationData()))
                            .build());
            return callbackResult(response.getResult(), response.getMessage());
        } catch (IllegalArgumentException invalid) {
            log.log(Level.WARNING, "Invalid TCC callback endpoint", invalid);
            return TccCallbackResult.permanentFailure(invalid.getMessage());
        } catch (RuntimeException failure) {
            log.log(Level.WARNING, "TCC cancel callback failed for "
                    + branch.getXid() + "/" + branch.getBranchId(), failure);
            return TccCallbackResult.retryable(failure.getMessage());
        } finally {
            shutdown(channel);
        }
    }

    private TccCallbackResult callbackResult(
            com.wuba.crm.tx.tcc.protocol.grpc.PhaseResult result, String message) {
        return switch (result) {
            case PHASE_SUCCESS -> TccCallbackResult.success();
            case PHASE_RETRYABLE -> TccCallbackResult.retryable(message);
            case PHASE_PERMANENT_FAILURE -> TccCallbackResult.permanentFailure(message);
            case PHASE_RESULT_UNSPECIFIED -> TccCallbackResult.retryable("unspecified phase result");
            default -> TccCallbackResult.retryable("unknown phase result");
        };
    }

    private ManagedChannel channel(String endpoint) {
        return ManagedChannelBuilder.forTarget(target(endpoint))
                .usePlaintext()
                .build();
    }

    /**
     * 把 endpoint 转成 gRPC target。
     *
     * @param endpoint 注册时保存的回调地址
     * @return host:port
     */
    static String target(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("callbackEndpoint must not be blank");
        }
        String value = endpoint.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return value;
        }
        URI uri = URI.create(value);
        if (uri.getHost() == null || uri.getPort() <= 0) {
            throw new IllegalArgumentException(
                    "callbackEndpoint must include host and port: " + endpoint);
        }
        return uri.getHost() + ":" + uri.getPort();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void shutdown(ManagedChannel channel) {
        if (channel != null) {
            channel.shutdownNow();
        }
    }
}
