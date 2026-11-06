package com.wuba.crm.tx.starter;

import com.wuba.crm.tx.outbox.config.BizCodeConfig;
import com.wuba.crm.tx.outbox.config.BizCodeRegistry;
import com.wuba.crm.tx.outbox.producer.OutboxConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Starter 对业务方暴露的唯一配置入口。
 *
 * <pre>
 * crm:
 *   tx:
 *     enabled: true
 *     auto-start: true
 *     stream-prefix: "tx:stream:"
 *     biz-codes: [lead, quota]
 *     outbox:
 *       linger: 10ms
 * </pre>
 */
@ConfigurationProperties(prefix = "crm.tx")
public class CrmTxProperties {

    private boolean enabled = true;
    private boolean autoStart = true;
    private String streamPrefix = "tx:stream:";
    private List<String> bizCodes = new ArrayList<>();
    private Outbox outbox = new Outbox();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public String getStreamPrefix() {
        return streamPrefix;
    }

    public void setStreamPrefix(String streamPrefix) {
        this.streamPrefix = streamPrefix;
    }

    public List<String> getBizCodes() {
        return bizCodes;
    }

    public void setBizCodes(List<String> bizCodes) {
        this.bizCodes = bizCodes;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public void setOutbox(Outbox outbox) {
        this.outbox = outbox;
    }

    public BizCodeRegistry toRegistry() {
        List<BizCodeConfig> configs = new ArrayList<>();
        for (String bizCode : bizCodes) {
            configs.add(new BizCodeConfig(bizCode));
        }
        return new BizCodeRegistry(configs);
    }

    public OutboxConfig toOutboxConfig() {
        return new OutboxConfig(
                streamPrefix,
                outbox.linger,
                outbox.batchSize,
                outbox.bufferCapacity,
                outbox.bufferCapacityBytes,
                outbox.scanBatch,
                outbox.scanInterval,
                outbox.retryAfter,
                outbox.maxRetries,
                outbox.senderThreads);
    }

    public static class Outbox {

        private Duration linger = Duration.ofMillis(10);
        private int batchSize = 256;
        private int bufferCapacity = 50_000;
        private long bufferCapacityBytes = 64L * 1024 * 1024;
        private int scanBatch = 1_000;
        private Duration scanInterval = Duration.ofMillis(10);
        private Duration retryAfter = Duration.ofSeconds(30);
        private int maxRetries = 5;
        private int senderThreads = 8;

        public Duration getLinger() {
            return linger;
        }

        public void setLinger(Duration linger) {
            this.linger = linger;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getBufferCapacity() {
            return bufferCapacity;
        }

        public void setBufferCapacity(int bufferCapacity) {
            this.bufferCapacity = bufferCapacity;
        }

        public long getBufferCapacityBytes() {
            return bufferCapacityBytes;
        }

        public void setBufferCapacityBytes(long bufferCapacityBytes) {
            this.bufferCapacityBytes = bufferCapacityBytes;
        }

        public int getScanBatch() {
            return scanBatch;
        }

        public void setScanBatch(int scanBatch) {
            this.scanBatch = scanBatch;
        }

        public Duration getScanInterval() {
            return scanInterval;
        }

        public void setScanInterval(Duration scanInterval) {
            this.scanInterval = scanInterval;
        }

        public Duration getRetryAfter() {
            return retryAfter;
        }

        public void setRetryAfter(Duration retryAfter) {
            this.retryAfter = retryAfter;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public int getSenderThreads() {
            return senderThreads;
        }

        public void setSenderThreads(int senderThreads) {
            this.senderThreads = senderThreads;
        }
    }
}
