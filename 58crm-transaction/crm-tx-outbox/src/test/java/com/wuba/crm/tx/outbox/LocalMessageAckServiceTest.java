package com.wuba.crm.tx.outbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 批量 ACK 只做一次仓储调用，不按消息逐条 RPC。
 */
class LocalMessageAckServiceTest {

    @Test
    void ackBatch_delegatesOneBatch() {
        FakeLocalMessageRepository repository = new FakeLocalMessageRepository();
        LocalMessageAckService service = new LocalMessageAckService(repository);

        int affected = service.ackBatch(List.of(
                new MessageAck("lead-1", "m-1"),
                new MessageAck("lead-1", "m-2"),
                new MessageAck("lead-2", "m-3")));

        assertEquals(3, affected);
        assertEquals(1, repository.ackCalls);
        assertEquals(3, repository.lastAcks.size());
    }

    @Test
    void ackBatch_rejectsOversizedBatch() {
        FakeLocalMessageRepository repository = new FakeLocalMessageRepository();
        LocalMessageAckService service = new LocalMessageAckService(repository);
        List<MessageAck> acks = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            acks.add(new MessageAck("lead-1", "m-" + i));
        }

        assertThrows(IllegalArgumentException.class, () -> service.ackBatch(acks));
        assertEquals(0, repository.ackCalls);
    }

    private static final class FakeLocalMessageRepository implements LocalMessageRepository {

        private int ackCalls;
        private List<MessageAck> lastAcks = new ArrayList<>();

        @Override
        public List<LocalMessage> findRelayCandidates(int limit) {
            return List.of();
        }

        @Override
        public int markSentBatch(List<LocalMessage> messages, java.time.Instant nextRetryAt) {
            return 0;
        }

        @Override
        public int ackBatch(List<MessageAck> acks) {
            ackCalls++;
            lastAcks = acks;
            return acks.size();
        }
    }
}
