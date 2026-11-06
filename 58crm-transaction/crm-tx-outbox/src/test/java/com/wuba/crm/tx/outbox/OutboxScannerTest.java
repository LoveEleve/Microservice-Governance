package com.wuba.crm.tx.outbox;

import com.wuba.crm.tx.common.MessageStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 扫描线程只入 buffer，不发送、不推进状态；in-flight 防止重复入队。
 */
class OutboxScannerTest {

    @Test
    void scanOnce_putsCandidatesIntoBufferAndDeduplicates() throws Exception {
        LocalMessage message = new LocalMessage(
                "m-1", "event-m-1", "lead", "lead-1", "LEAD_ASSIGNED",
                "{}", null, MessageStatus.INIT, 0);
        FakeRepository repository = new FakeRepository(List.of(message));
        OutboxBuffer buffer = new OutboxBuffer(10, 4096);
        OutboxInFlight inFlight = new OutboxInFlight();
        OutboxScanner scanner = new OutboxScanner(repository, buffer, inFlight, 100);

        assertEquals(1, scanner.scanOnce());
        assertEquals(1, buffer.size());
        assertEquals(0, scanner.scanOnce());
        assertEquals(1, buffer.size());
        assertEquals(1, inFlight.size());
    }

    private static final class FakeRepository implements LocalMessageRepository {

        private final List<LocalMessage> messages;

        private FakeRepository(List<LocalMessage> messages) {
            this.messages = messages;
        }

        @Override
        public List<LocalMessage> findRelayCandidates(int limit) {
            return messages;
        }

        @Override
        public int markSentBatch(List<LocalMessage> messages, java.time.Instant nextRetryAt) {
            return 0;
        }

        @Override
        public int markFailedBatch(List<LocalMessage> messages, java.time.Instant nextRetryAt, int maxRetries) {
            return 0;
        }
    }
}
