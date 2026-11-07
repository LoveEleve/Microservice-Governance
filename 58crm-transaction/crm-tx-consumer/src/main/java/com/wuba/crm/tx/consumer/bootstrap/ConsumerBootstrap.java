package com.wuba.crm.tx.consumer.bootstrap;

import com.wuba.crm.tx.consumer.ack.AckBatcher;
import com.wuba.crm.tx.consumer.ack.RedisStreamAcknowledger;
import com.wuba.crm.tx.consumer.lease.InFlightRegistry;
import com.wuba.crm.tx.consumer.lease.LeaseManager;
import com.wuba.crm.tx.consumer.lease.NettyLeaseWheel;
import com.wuba.crm.tx.consumer.lease.RepositoryLeaseRenewer;
import com.wuba.crm.tx.consumer.persist.ConsumePersister;
import com.wuba.crm.tx.consumer.persistence.ConsumerRecordRepository;
import com.wuba.crm.tx.consumer.persistence.IdempotentRepository;
import com.wuba.crm.tx.consumer.pull.BoundedStreamQueue;
import com.wuba.crm.tx.consumer.pull.RedisStreamSource;
import com.wuba.crm.tx.consumer.pull.StreamPuller;
import com.wuba.crm.tx.consumer.recovery.PendingRecovery;
import com.wuba.crm.tx.consumer.worker.ConsumerWorker;
import com.wuba.crm.tx.consumer.worker.ConsumerWorkerConfig;
import com.wuba.crm.tx.consumer.worker.HandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConsumerBootstrap implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ConsumerBootstrap.class);

    private final ConsumerRuntimeConfig config;
    private final ConsumerRecordRepository repository;
    private final IdempotentRepository idempotent;
    private final HandlerRegistry handlers;
    private final TransactionOperations transactions;
    private final StringRedisTemplate redisTemplate;
    private final RedisStreamGroupInitializer groupInitializer;
    private final AtomicBoolean started = new AtomicBoolean();

    private LeaseManager leaseManager;
    private AckBatcher ackBatcher;
    private List<ConsumerWorker> workers = List.of();
    private ConsumePersister persister;
    private PendingRecovery pendingRecovery;
    private StreamPuller puller;

    public ConsumerBootstrap(
            ConsumerRuntimeConfig config,
            ConsumerRecordRepository repository,
            IdempotentRepository idempotent,
            HandlerRegistry handlers,
            TransactionOperations transactions,
            StringRedisTemplate redisTemplate) {
        this(config, repository, idempotent, handlers, transactions, redisTemplate,
                new RedisStreamGroupInitializer(redisTemplate));
    }

    ConsumerBootstrap(
            ConsumerRuntimeConfig config,
            ConsumerRecordRepository repository,
            IdempotentRepository idempotent,
            HandlerRegistry handlers,
            TransactionOperations transactions,
            StringRedisTemplate redisTemplate,
            RedisStreamGroupInitializer groupInitializer) {
        this.config = Objects.requireNonNull(config, "config");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.idempotent = Objects.requireNonNull(idempotent, "idempotent");
        this.handlers = Objects.requireNonNull(handlers, "handlers");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.groupInitializer = Objects.requireNonNull(groupInitializer, "groupInitializer");
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        try {
            groupInitializer.ensureGroup(config.stream(), config.group());
            log.info("consumer bootstrap starting, bizCode={}, stream={}, group={}, consumer={}",
                    config.bizCode(), config.stream(), config.group(), config.consumerId());

            BoundedStreamQueue queue = new BoundedStreamQueue(config.queueCapacity());
            RedisStreamSource source = new RedisStreamSource(redisTemplate);
            puller = new StreamPuller(
                    source, queue, config.stream(), config.group(), config.consumerId(),
                    config.pullCount(), config.pullBlock());
            persister = new ConsumePersister(
                    queue, repository, config.persistBatchSize(), config.persistPollTimeout());
            leaseManager = new LeaseManager(
                    new NettyLeaseWheel(),
                    new InFlightRegistry(),
                    new RepositoryLeaseRenewer(repository, config.leaseDuration()),
                    config.leaseRenewAfter(),
                    task -> log.warn("consumer lease lost, bizCode={}, msgId={}, epoch={}",
                            task.bizCode(), task.msgId(), task.ownerEpoch()));
            RedisStreamAcknowledger acknowledger = new RedisStreamAcknowledger(redisTemplate);
            ackBatcher = new AckBatcher(
                    acknowledger,
                    config.ackBatchSize(),
                    config.ackQueueCapacity(),
                    config.ackFlushInterval());
            List<ConsumerWorker> consumerWorkers = new ArrayList<>(config.workerThreads());
            for (int i = 0; i < config.workerThreads(); i++) {
                ConsumerWorkerConfig workerConfig = new ConsumerWorkerConfig(
                        config.bizCode(), config.stream(), config.group(),
                        config.consumerId() + "-" + i,
                        config.workerBatchSize(), config.leaseDuration(), config.maxProcessing(),
                        config.retryAfter(), config.maxRetries(), config.workerIdlePoll());
                consumerWorkers.add(new ConsumerWorker(
                        workerConfig, repository, idempotent, handlers, transactions,
                        leaseManager, ackBatcher));
            }
            workers = List.copyOf(consumerWorkers);
            pendingRecovery = new PendingRecovery(
                    redisTemplate, config.stream(), config.group(), config.consumerId() + "-recovery",
                    repository, acknowledger, config.recoveryBatchSize(), config.recoveryScanInterval());

            leaseManager.start();
            ackBatcher.start();
            for (ConsumerWorker worker : workers) {
                worker.start();
            }
            persister.start();
            pendingRecovery.start();
            puller.start();
            log.info("consumer bootstrap started, bizCode={}, stream={}, group={}",
                    config.bizCode(), config.stream(), config.group());
        } catch (RuntimeException ex) {
            close();
            throw ex;
        }
    }

    @Override
    public void close() {
        closeQuietly(puller);
        closeQuietly(pendingRecovery);
        closeQuietly(persister);
        for (ConsumerWorker worker : workers) {
            closeQuietly(worker);
        }
        closeQuietly(ackBatcher);
        closeQuietly(leaseManager);
        started.set(false);
        log.info("consumer bootstrap stopped, bizCode={}, stream={}", config.bizCode(), config.stream());
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ex) {
            log.warn("consumer component close failed", ex);
        }
    }
}
