package com.wuba.crm.tx.consumer.recovery;

import com.wuba.crm.tx.consumer.ack.StreamAcknowledger;
import com.wuba.crm.tx.consumer.domain.ConsumeStatus;
import com.wuba.crm.tx.consumer.domain.ConsumedMessage;
import com.wuba.crm.tx.consumer.domain.ConsumerRecord;
import com.wuba.crm.tx.consumer.persistence.ConsumerRecordRepository;
import com.wuba.crm.tx.consumer.pull.StreamMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PendingRecovery implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PendingRecovery.class);
    private static final long CLOSE_JOIN_MILLIS = 5_000L;

    private final StringRedisTemplate redisTemplate;
    private final String stream;
    private final String group;
    private final String consumer;
    private final ConsumerRecordRepository repository;
    private final StreamAcknowledger acknowledger;
    private final int batchSize;
    private final Duration scanInterval;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Thread thread;

    public PendingRecovery(
            StringRedisTemplate redisTemplate,
            String stream,
            String group,
            String consumer,
            ConsumerRecordRepository repository,
            StreamAcknowledger acknowledger,
            int batchSize,
            Duration scanInterval) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.stream = requireText(stream, "stream");
        this.group = requireText(group, "group");
        this.consumer = requireText(consumer, "consumer");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.acknowledger = Objects.requireNonNull(acknowledger, "acknowledger");
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        this.batchSize = batchSize;
        this.scanInterval = Objects.requireNonNull(scanInterval, "scanInterval");
        if (scanInterval.isZero() || scanInterval.isNegative()) {
            throw new IllegalArgumentException("scanInterval must be positive");
        }
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(this::runLoop, "tx-consumer-pending-recovery-" + stream);
        worker.setDaemon(true);
        this.thread = worker;
        worker.start();
        log.info("pending recovery started, stream={}, group={}, consumer={}, batchSize={}, scanInterval={}",
                stream, group, consumer, batchSize, scanInterval);
    }

    public int recoverOnce() {
        PendingMessages pending = redisTemplate.opsForStream()
                .pending(stream, group, Range.unbounded(), batchSize);
        if (pending == null || pending.isEmpty()) {
            return 0;
        }
        int recovered = 0;
        for (PendingMessage message : pending) {
            try {
                if (recover(message)) {
                    recovered++;
                }
            } catch (RuntimeException ex) {
                log.warn("pending recovery failed, stream={}, entryId={}",
                        stream, message.getIdAsString(), ex);
            }
        }
        log.debug("pending recovery scan, stream={}, pending={}, recovered={}",
                stream, pending.size(), recovered);
        return recovered;
    }

    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() {
        running.set(false);
        Thread worker = thread;
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join(CLOSE_JOIN_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("pending recovery stopped, stream={}, group={}", stream, group);
    }

    private boolean recover(PendingMessage pending) {
        String entryId = pending.getIdAsString();
        ConsumerRecord record = repository.findByStreamEntryId(entryId);
        if (record == null) {
            return recoverMissing(entryId);
        }
        if (record.status() == ConsumeStatus.CONSUMED || record.status() == ConsumeStatus.DEAD) {
            acknowledger.acknowledge(stream, group, entryId);
            return true;
        }
        if (record.status() == ConsumeStatus.READY) {
            return false;
        }
        Instant now = Instant.now();
        if (record.leaseUntil() != null && record.leaseUntil().isAfter(now)) {
            return false;
        }
        repository.markExpiredProcessingReady(record.msgId(), now);
        return true;
    }

    private boolean recoverMissing(String entryId) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .claim(stream, group, consumer, Duration.ZERO,
                        org.springframework.data.redis.connection.stream.RecordId.of(entryId));
        if (records == null || records.isEmpty()) {
            acknowledger.acknowledge(stream, group, entryId);
            return false;
        }
        ConsumedMessage message = StreamMessageDecoder.decode(records.get(0));
        repository.insertReadyBatch(List.of(message), Instant.now());
        return true;
    }

    private void runLoop() {
        while (running.get()) {
            try {
                recoverOnce();
                Thread.sleep(scanInterval.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ex) {
                if (!running.get()) {
                    return;
                }
                log.warn("pending recovery scan failed, stream={}", stream, ex);
                try {
                    Thread.sleep(scanInterval.toMillis());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
