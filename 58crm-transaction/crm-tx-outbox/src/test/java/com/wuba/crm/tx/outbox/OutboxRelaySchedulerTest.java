package com.wuba.crm.tx.outbox;

import com.wuba.crm.tx.common.MessageStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 10ms 窗口内可以连续冲多批，但每批最多 256 条由 Relay 参数控制。
 */
class OutboxRelaySchedulerTest {

    @Test
    void flush_runsMultipleBatchesInOneWindow() {
        FakeRepository repository = new FakeRepository();
        FakeQueue queue = new FakeQueue();
        repository.candidates.add(message("m-1"));
        repository.candidates.add(message("m-2"));
        repository.candidates.add(message("m-3"));

        OutboxRelay relay = new OutboxRelay(repository, queue, Duration.ofSeconds(30));
        OutboxRelayScheduler scheduler =
                new OutboxRelayScheduler(relay, Duration.ofMillis(10), 2, 3);

        int relayed = scheduler.flush();

        assertEquals(3, relayed);
        assertTrue(queue.batchCalls >= 1);
        scheduler.close();
    }

    private static LocalMessage message(String msgId) {
        return new LocalMessage(
                msgId,
                "event-" + msgId,
                "lead",
                "lead-1",
                "LEAD_ASSIGNED",
                "{}",
                null,
                MessageStatus.INIT,
                0);
    }

    private static final class FakeRepository implements LocalMessageRepository {

        private final List<LocalMessage> candidates = new ArrayList<>();
        private boolean served;

        @Override
        public List<LocalMessage> findRelayCandidates(int limit) {
            if (served) {
                return List.of();
            }
            served = true;
            return candidates;
        }

        @Override
        public int markSentBatch(List<LocalMessage> messages, java.time.Instant nextRetryAt) {
            return messages.size();
        }

        @Override
        public int ackBatch(List<MessageAck> acks) {
            return 0;
        }
    }

    private static final class FakeQueue implements RedisListQueue {

        private int batchCalls;

        @Override
        public void enqueue(String bizCode, QueueEnvelope envelope) {
            batchCalls++;
        }

        @Override
        public void enqueueBatch(String bizCode, List<QueueEnvelope> envelopes) {
            batchCalls++;
        }

        @Override
        public QueueEnvelope dequeue(String bizCode, Duration timeout) {
            return null;
        }
    }
}
