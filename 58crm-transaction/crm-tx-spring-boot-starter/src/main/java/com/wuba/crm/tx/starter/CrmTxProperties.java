package com.wuba.crm.tx.starter;

import com.wuba.crm.tx.consumer.bootstrap.ConsumerRuntimeConfig;
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
    private Consumer consumer = new Consumer();

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

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    public ConsumerRuntimeConfig toConsumerConfig(String bizCode, String consumerId) {
        return new ConsumerRuntimeConfig(
                bizCode,
                consumerId,
                streamPrefix,
                consumer.groupPrefix,
                consumer.queueCapacity,
                consumer.persistBatchSize,
                consumer.persistPollTimeout,
                consumer.workerBatchSize,
                consumer.workerThreads,
                consumer.leaseDuration,
                consumer.maxProcessing,
                consumer.retryAfter,
                consumer.maxRetries,
                consumer.workerIdlePoll,
                consumer.leaseRenewAfter,
                consumer.ackBatchSize,
                consumer.ackQueueCapacity,
                consumer.ackFlushInterval,
                consumer.recoveryBatchSize,
                consumer.recoveryScanInterval,
                consumer.pullCount,
                consumer.pullBlock);
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
                outbox.senderThreads,
                outbox.perBizCapacity,
                outbox.perBizCapacityBytes);
    }

    public static class Consumer {

        private boolean enabled = true;
        private String groupPrefix = "tx-group-";
        private String consumerIdPrefix = "crm-consumer";
        private int queueCapacity = 10_000;
        private int persistBatchSize = 500;
        private Duration persistPollTimeout = Duration.ofMillis(100);
        private int workerBatchSize = 200;
        private int workerThreads = 8;
        private Duration leaseDuration = Duration.ofSeconds(60);
        private Duration maxProcessing = Duration.ofMinutes(5);
        private Duration retryAfter = Duration.ofSeconds(30);
        private int maxRetries = 3;
        private Duration workerIdlePoll = Duration.ofMillis(10);
        private Duration leaseRenewAfter = Duration.ofSeconds(20);
        private int ackBatchSize = 200;
        private int ackQueueCapacity = 100_000;
        private Duration ackFlushInterval = Duration.ofMillis(100);
        private int recoveryBatchSize = 100;
        private Duration recoveryScanInterval = Duration.ofSeconds(30);
        private int pullCount = 500;
        private Duration pullBlock = Duration.ofSeconds(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getGroupPrefix() {
            return groupPrefix;
        }

        public void setGroupPrefix(String groupPrefix) {
            this.groupPrefix = groupPrefix;
        }

        public String getConsumerIdPrefix() {
            return consumerIdPrefix;
        }

        public void setConsumerIdPrefix(String consumerIdPrefix) {
            this.consumerIdPrefix = consumerIdPrefix;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getPersistBatchSize() {
            return persistBatchSize;
        }

        public void setPersistBatchSize(int persistBatchSize) {
            this.persistBatchSize = persistBatchSize;
        }

        public Duration getPersistPollTimeout() {
            return persistPollTimeout;
        }

        public void setPersistPollTimeout(Duration persistPollTimeout) {
            this.persistPollTimeout = persistPollTimeout;
        }

        public int getWorkerBatchSize() {
            return workerBatchSize;
        }

        public void setWorkerBatchSize(int workerBatchSize) {
            this.workerBatchSize = workerBatchSize;
        }

        public int getWorkerThreads() {
            return workerThreads;
        }

        public void setWorkerThreads(int workerThreads) {
            this.workerThreads = workerThreads;
        }

        public Duration getLeaseDuration() {
            return leaseDuration;
        }

        public void setLeaseDuration(Duration leaseDuration) {
            this.leaseDuration = leaseDuration;
        }

        public Duration getMaxProcessing() {
            return maxProcessing;
        }

        public void setMaxProcessing(Duration maxProcessing) {
            this.maxProcessing = maxProcessing;
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

        public Duration getWorkerIdlePoll() {
            return workerIdlePoll;
        }

        public void setWorkerIdlePoll(Duration workerIdlePoll) {
            this.workerIdlePoll = workerIdlePoll;
        }

        public Duration getLeaseRenewAfter() {
            return leaseRenewAfter;
        }

        public void setLeaseRenewAfter(Duration leaseRenewAfter) {
            this.leaseRenewAfter = leaseRenewAfter;
        }

        public int getAckBatchSize() {
            return ackBatchSize;
        }

        public void setAckBatchSize(int ackBatchSize) {
            this.ackBatchSize = ackBatchSize;
        }

        public int getAckQueueCapacity() {
            return ackQueueCapacity;
        }

        public void setAckQueueCapacity(int ackQueueCapacity) {
            this.ackQueueCapacity = ackQueueCapacity;
        }

        public Duration getAckFlushInterval() {
            return ackFlushInterval;
        }

        public void setAckFlushInterval(Duration ackFlushInterval) {
            this.ackFlushInterval = ackFlushInterval;
        }

        public int getRecoveryBatchSize() {
            return recoveryBatchSize;
        }

        public void setRecoveryBatchSize(int recoveryBatchSize) {
            this.recoveryBatchSize = recoveryBatchSize;
        }

        public Duration getRecoveryScanInterval() {
            return recoveryScanInterval;
        }

        public void setRecoveryScanInterval(Duration recoveryScanInterval) {
            this.recoveryScanInterval = recoveryScanInterval;
        }

        public int getPullCount() {
            return pullCount;
        }

        public void setPullCount(int pullCount) {
            this.pullCount = pullCount;
        }

        public Duration getPullBlock() {
            return pullBlock;
        }

        public void setPullBlock(Duration pullBlock) {
            this.pullBlock = pullBlock;
        }
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
        private int perBizCapacity = 10_000;
        private long perBizCapacityBytes = 16L * 1024 * 1024;

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

        public int getPerBizCapacity() {
            return perBizCapacity;
        }

        public void setPerBizCapacity(int perBizCapacity) {
            this.perBizCapacity = perBizCapacity;
        }

        public long getPerBizCapacityBytes() {
            return perBizCapacityBytes;
        }

        public void setPerBizCapacityBytes(long perBizCapacityBytes) {
            this.perBizCapacityBytes = perBizCapacityBytes;
        }
    }
}
