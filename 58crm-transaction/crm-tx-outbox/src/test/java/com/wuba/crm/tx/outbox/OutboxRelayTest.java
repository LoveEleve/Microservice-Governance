package com.wuba.crm.tx.outbox;

import com.wuba.crm.tx.common.MessageStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C-001.2 的生产方投递闭环。
 */
class OutboxRelayTest {

    @Test
    void relayOnce_pushesEnvelopeAndMarksSent() {
        FakeLocalMessageRepository repository = new FakeLocalMessageRepository();
        FakeRedisListQueue queue = new FakeRedisListQueue();
        LocalMessage message = message("m-1", "lead-1", "lead");
        repository.candidates.add(message);
        OutboxRelay relay = new OutboxRelay(repository, queue, java.time.Duration.ofSeconds(30));

        int relayed = relay.relayOnce(10);

        assertEquals(1, relayed);
        assertEquals(1, queue.envelopes.size());
        QueueEnvelope envelope = queue.envelopes.get(0);
        assertEquals("lead-1", envelope.bizId());
        assertEquals("m-1", envelope.msgId());
        assertEquals("{}", envelope.payload());
        assertTrue(repository.sent);
        assertFalse(repository.lastNextRetryAt == null);
    }

    @Test
    void relayOnce_doesNotMarkSentWhenRedisFails() {
        FakeLocalMessageRepository repository = new FakeLocalMessageRepository();
        FakeRedisListQueue queue = new FakeRedisListQueue();
        queue.fail = true;
        repository.candidates.add(message("m-1", "lead-1", "lead"));
        OutboxRelay relay = new OutboxRelay(repository, queue, java.time.Duration.ofSeconds(30));

        int relayed = relay.relayOnce(10);

        assertEquals(0, relayed);
        assertFalse(repository.sent);
    }

    private static LocalMessage message(String msgId, String bizId, String bizCode) {
        return new LocalMessage(
                msgId,
                "event-" + msgId,
                bizCode,
                bizId,
                "LEAD_ASSIGNED",
                "{}",
                null,
                MessageStatus.INIT,
                0);
    }

    private static final class FakeLocalMessageRepository implements LocalMessageRepository {

        private final List<LocalMessage> candidates = new ArrayList<>();
        private boolean sent;
        private java.time.Instant lastNextRetryAt;

        @Override
        public List<LocalMessage> findRelayCandidates(int limit) {
            return candidates;
        }

        @Override
        public boolean markSent(String bizId, String msgId, java.time.Instant nextRetryAt) {
            sent = true;
            lastNextRetryAt = nextRetryAt;
            return true;
        }

        @Override
        public int ackBatch(List<MessageAck> acks) {
            return 0;
        }
    }

    private static final class FakeRedisListQueue implements RedisListQueue {

        private final List<QueueEnvelope> envelopes = new ArrayList<>();
        private boolean fail;

        @Override
        public void enqueue(String bizCode, QueueEnvelope envelope) {
            if (fail) {
                throw new IllegalStateException("redis unavailable");
            }
            envelopes.add(envelope);
        }

        @Override
        public QueueEnvelope dequeue(String bizCode, java.time.Duration timeout) {
            return null;
        }
    }
}
