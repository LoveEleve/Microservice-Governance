package com.wuba.crm.tx.consumer.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsumerRecordTest {

    @Test
    void ready_initializesUnclaimedRecord() {
        ConsumedMessage message = new ConsumedMessage(
                "m-1", "lead", "lead-1", "LEAD_ASSIGNED", "1-0", "{}", null);

        ConsumerRecord record = ConsumerRecord.ready(message, Instant.parse("2026-01-01T00:00:00Z"));

        assertEquals(ConsumeStatus.READY, record.status());
        assertEquals(0, record.ownerEpoch());
        assertEquals(0, record.claimCount());
        assertNull(record.ownerToken());
        assertNull(record.leaseUntil());
    }

    @Test
    void payloadAndPayloadRef_bothMissing_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConsumedMessage("m-1", "lead", "lead-1",
                        "LEAD_ASSIGNED", "1-0", null, " "));
    }

    @Test
    void claimRequest_limitOutsideRange_isRejected() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> new ClaimRequest("lead", "c-1", "o-1", 0, now, now.plusSeconds(60)));
        assertThrows(IllegalArgumentException.class,
                () -> new ClaimRequest("lead", "c-1", "o-1", 1001, now, now.plusSeconds(60)));
    }
}
