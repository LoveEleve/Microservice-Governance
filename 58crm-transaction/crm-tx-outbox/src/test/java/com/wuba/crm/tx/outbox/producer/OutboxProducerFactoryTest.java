package com.wuba.crm.tx.outbox.producer;

import com.wuba.crm.tx.outbox.config.BizCodeRegistry;
import com.wuba.crm.tx.outbox.domain.LocalMessage;
import com.wuba.crm.tx.outbox.persistence.LocalMessageRepository;
import com.wuba.crm.tx.outbox.pipeline.interceptor.OutboxInterceptorChain;
import com.wuba.crm.tx.outbox.pipeline.route.BizCodePartitioner;
import com.wuba.crm.tx.outbox.pipeline.serializer.DefaultOutboxSerializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 装配层必须把 3 个 bizCode 截断成最多 3 个 Sender。
 */
class OutboxProducerFactoryTest {

    @Test
    void factory_capsSenderCountAtBizCodeCount() {
        OutboxBuffer buffer = new OutboxBuffer(100, 1024 * 1024);
        OutboxInFlight inFlight = new OutboxInFlight();
        OutboxConfig config = new OutboxConfig(
                "tx:test:", Duration.ofMillis(1), 10, 100, 1024 * 1024,
                10, Duration.ofMillis(10), Duration.ofSeconds(30), 3, 8);

        OutboxProducer producer = OutboxProducerFactory.create(
                new OutboxScanner(new EmptyRepository(), buffer, inFlight, 10, BizCodeRegistry.of("lead", "quota", "contract")),
                buffer,
                (stream, records) -> java.util.List.of(),
                new OutboxInterceptorChain(List.of()),
                new BizCodePartitioner(),
                new DefaultOutboxSerializer(),
                new EmptyRepository(),
                inFlight,
                config,
                BizCodeRegistry.of("lead", "quota", "contract"));

        assertEquals(3, producer.senderCount());
        producer.close();
    }

    private static final class EmptyRepository implements LocalMessageRepository {

        @Override
        public List<LocalMessage> findRelayCandidates(int limit) {
            return List.of();
        }

        @Override
        public int markSentBatch(List<LocalMessage> messages, Instant nextRetryAt) {
            return 0;
        }

        @Override
        public int markFailedBatch(List<LocalMessage> messages, Instant nextRetryAt, int maxRetries) {
            return 0;
        }
    }
}
