package com.wuba.crm.tx.outbox;

import com.wuba.crm.tx.common.MessageStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 队列信封必须带分片键和正文，否则消费方无法独立重试。
 */
class QueueEnvelopeTest {

    @Test
    void from_copiesBizIdAndPayload() {
        LocalMessage message = new LocalMessage(
                "m-1",
                "event-1",
                "lead",
                "lead-1",
                "LEAD_ASSIGNED",
                "{\"salesId\":1001}",
                null,
                MessageStatus.INIT,
                0);

        QueueEnvelope envelope = QueueEnvelope.from(message);

        assertEquals("lead-1", envelope.bizId());
        assertEquals("m-1", envelope.msgId());
        assertEquals("LEAD_ASSIGNED", envelope.eventType());
        assertEquals("{\"salesId\":1001}", envelope.payload());
    }

    @Test
    void envelope_requiresPayloadOrRef() {
        assertThrows(IllegalArgumentException.class,
                () -> new QueueEnvelope("lead-1", "m-1", "event-1", "LEAD_ASSIGNED", null, null));
    }
}
