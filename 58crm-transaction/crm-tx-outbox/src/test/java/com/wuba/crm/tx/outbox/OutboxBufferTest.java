package com.wuba.crm.tx.outbox;

import com.wuba.crm.tx.common.MessageStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 有界 buffer：容量、批量 drain、空队列超时。
 */
class OutboxBufferTest {

    @Test
    void buffer_isBoundedAndSupportsBatchDrain() throws Exception {
        OutboxBuffer buffer = new OutboxBuffer(2);
        buffer.put(record("m-1"));
        buffer.put(record("m-2"));

        assertEquals(0, buffer.remainingCapacity());
        List<OutboxRecord> batch = new ArrayList<>();
        assertEquals(2, buffer.drainTo(batch, 10));
        assertEquals(2, batch.size());
        assertNull(buffer.poll(Duration.ofMillis(1)));
    }

    private static OutboxRecord record(String msgId) {
        return new OutboxRecord(
                msgId, "event-" + msgId, "lead", "lead-1", "LEAD_ASSIGNED",
                "{}", null, MessageStatus.INIT, 0);
    }
}
