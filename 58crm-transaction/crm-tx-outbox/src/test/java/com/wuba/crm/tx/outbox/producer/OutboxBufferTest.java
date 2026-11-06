package com.wuba.crm.tx.outbox.producer;

import com.wuba.crm.tx.outbox.api.*;
import com.wuba.crm.tx.outbox.domain.*;
import com.wuba.crm.tx.outbox.persistence.*;
import com.wuba.crm.tx.outbox.producer.*;
import com.wuba.crm.tx.outbox.pipeline.interceptor.*;
import com.wuba.crm.tx.outbox.pipeline.route.*;
import com.wuba.crm.tx.outbox.pipeline.serializer.*;
import com.wuba.crm.tx.outbox.pipeline.stream.*;


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
    void buffer_keepsBusinessLinesSeparate() throws Exception {
        OutboxBuffer buffer = new OutboxBuffer(10, 4096);
        buffer.put(record("lead-1", "lead"));
        buffer.put(record("quota-1", "quota"));

        List<OutboxRecord> lead = new ArrayList<>();
        assertEquals(1, buffer.drain("lead", lead, 10));
        assertEquals("lead", lead.get(0).bizCode());
        assertEquals(1, buffer.size());

        OutboxRecord quota = buffer.poll(java.time.Duration.ofMillis(1));
        assertEquals("quota", quota.bizCode());
    }

    @Test
    void buffer_isBoundedAndSupportsBatchDrain() throws Exception {
        OutboxBuffer buffer = new OutboxBuffer(2, 1024);
        buffer.put(record("m-1"));
        buffer.put(record("m-2"));

        assertEquals(0, buffer.remainingCapacity());
        List<OutboxRecord> batch = new ArrayList<>();
        assertEquals(2, buffer.drain("lead", batch, 10));
        assertEquals(2, batch.size());
        assertNull(buffer.poll(Duration.ofMillis(1)));
    }

    private static OutboxRecord record(String msgId) {
        return record(msgId, "lead");
    }

    private static OutboxRecord record(String msgId, String bizCode) {
        return new OutboxRecord(
                msgId, "event-" + msgId, bizCode, "lead-1", "LEAD_ASSIGNED",
                "{}", null, MessageStatus.INIT, 0);
    }
}
