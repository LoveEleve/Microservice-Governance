package com.wuba.crm.tx.outbox.pipeline.interceptor;

import com.wuba.crm.tx.outbox.api.*;
import com.wuba.crm.tx.outbox.config.*;
import com.wuba.crm.tx.outbox.domain.*;
import com.wuba.crm.tx.outbox.persistence.*;
import com.wuba.crm.tx.outbox.producer.*;
import com.wuba.crm.tx.outbox.pipeline.interceptor.*;
import com.wuba.crm.tx.outbox.pipeline.route.*;
import com.wuba.crm.tx.outbox.pipeline.serializer.*;
import com.wuba.crm.tx.outbox.pipeline.stream.*;


import com.wuba.crm.tx.common.MessageStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 验证 Kafka 风格拦截器链：顺序、可变返回值、异常隔离。
 */
class OutboxInterceptorChainTest {

    @Test
    void onSend_runsInOrderAndPassesMutatedRecord() {
        List<String> calls = new ArrayList<>();
        OutboxInterceptor first = new OutboxInterceptor() {
            @Override
            public OutboxRecord onSend(OutboxRecord record) {
                calls.add("first");
                return new OutboxRecord(
                        record.msgId(), record.eventKey() + ":v1", record.bizCode(),
                        record.bizId(), record.eventType(), record.payload(),
                        record.payloadRef(), record.status(), record.retryCount());
            }

            @Override
            public void onAcknowledgement(OutboxSendResult result, Throwable error) {
            }
        };
        OutboxInterceptor second = new OutboxInterceptor() {
            @Override
            public OutboxRecord onSend(OutboxRecord record) {
                calls.add(record.eventKey());
                return record;
            }

            @Override
            public void onAcknowledgement(OutboxSendResult result, Throwable error) {
            }
        };

        OutboxRecord result = new OutboxInterceptorChain(List.of(first, second))
                .onSend(record());

        assertEquals(List.of("first", "event-m-1:v1"), calls);
        assertEquals("event-m-1:v1", result.eventKey());
    }

    @Test
    void onSend_isolatesInterceptorFailure() {
        OutboxInterceptor failing = new OutboxInterceptor() {
            @Override
            public OutboxRecord onSend(OutboxRecord record) {
                throw new IllegalStateException("boom");
            }

            @Override
            public void onAcknowledgement(OutboxSendResult result, Throwable error) {
            }
        };

        OutboxRecord original = record();
        OutboxRecord result = new OutboxInterceptorChain(List.of(failing)).onSend(original);

        assertSame(original, result);
    }

    private static OutboxRecord record() {
        return new OutboxRecord(
                "m-1", "event-m-1", "lead", "lead-1", "LEAD_ASSIGNED",
                "{}", null, MessageStatus.INIT, 0);
    }
}
