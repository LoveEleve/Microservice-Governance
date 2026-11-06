package com.wuba.crm.tx.outbox.producer;

import com.wuba.crm.tx.outbox.config.BizCodeRegistry;
import com.wuba.crm.tx.outbox.persistence.LocalMessageRepository;
import com.wuba.crm.tx.outbox.pipeline.interceptor.OutboxInterceptorChain;
import com.wuba.crm.tx.outbox.pipeline.route.OutboxPartitioner;
import com.wuba.crm.tx.outbox.pipeline.serializer.OutboxSerializer;
import com.wuba.crm.tx.outbox.pipeline.stream.RedisStreamPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 生产端装配入口：把外部 bizCode 配置转换成固定数量的 Sender shard。
 *
 * <p>调用方不需要自己计算 shard，也不会出现线程数大于队列数。</p>
 */
public final class OutboxProducerFactory {

    private OutboxProducerFactory() {
    }

    public static OutboxProducer create(
            OutboxScanner scanner,
            OutboxBuffer buffer,
            RedisStreamPublisher streamPublisher,
            OutboxInterceptorChain interceptors,
            OutboxPartitioner partitioner,
            OutboxSerializer serializer,
            LocalMessageRepository repository,
            OutboxInFlight inFlight,
            OutboxConfig config,
            BizCodeRegistry registry) {

        Map<Integer, Set<String>> assignments = SenderAssignment.assign(
                registry.bizCodes(), config.senderThreads());

        List<OutboxSender> senders = new ArrayList<>(assignments.size());
        for (Set<String> assignedBizCodes : assignments.values()) {
            senders.add(new OutboxSender(
                    buffer,
                    streamPublisher,
                    interceptors,
                    partitioner,
                    serializer,
                    repository,
                    config,
                    inFlight,
                    assignedBizCodes));
        }
        return new OutboxProducer(scanner, senders, interceptors, config);
    }
}
