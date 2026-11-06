package com.wuba.crm.tx.outbox.persistence;

import com.wuba.crm.tx.outbox.api.*;
import com.wuba.crm.tx.outbox.config.*;
import com.wuba.crm.tx.outbox.domain.*;
import com.wuba.crm.tx.outbox.persistence.*;
import com.wuba.crm.tx.outbox.producer.*;
import com.wuba.crm.tx.outbox.pipeline.interceptor.*;
import com.wuba.crm.tx.outbox.pipeline.route.*;
import com.wuba.crm.tx.outbox.pipeline.serializer.*;
import com.wuba.crm.tx.outbox.pipeline.stream.*;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * C-001.1 的核心验收：业务事件必须被写成一条 INIT 消息。
 */
@ExtendWith(MockitoExtension.class)
class MybatisOutboxPublisherTest {

    @Mock
    private LocalMessageMapper localMessageMapper;

    @Test
    void publish_writesInitMessageWithBusinessFields() {
        MybatisOutboxPublisher publisher = new MybatisOutboxPublisher(localMessageMapper, BizCodeRegistry.of("lead"));
        BusinessEvent event = new BusinessEvent(
                "lead-assigned:lead-1:assign-1",
                "lead",
                "lead-1",
                "LEAD_ASSIGNED",
                "{\"salesId\":1001}");

        String msgId = publisher.publish(event);

        ArgumentCaptor<LocalMessageEntity> captor = ArgumentCaptor.forClass(LocalMessageEntity.class);
        verify(localMessageMapper).insert(captor.capture());
        LocalMessageEntity saved = captor.getValue();

        assertNotNull(msgId);
        assertEquals(msgId, saved.getMsgId());
        assertEquals("lead-assigned:lead-1:assign-1", saved.getEventKey());
        assertEquals("lead", saved.getBizCode());
        assertEquals("lead-1", saved.getBizId());
        assertEquals("LEAD_ASSIGNED", saved.getEventType());
        assertEquals("{\"salesId\":1001}", saved.getPayload());
        assertEquals("INIT", saved.getStatus());
        assertEquals(0, saved.getRetryCount());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void publish_rejectsBlankEventKeyBeforeWriting() {
        MybatisOutboxPublisher publisher = new MybatisOutboxPublisher(localMessageMapper, BizCodeRegistry.of("lead"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new BusinessEvent(" ", "lead", "lead-1", "LEAD_ASSIGNED", "{}"));

        assertTrue(error.getMessage().contains("eventKey"));
    }

    @Test
    void publish_rejectsUnknownBizCodeBeforeWriting() {
        MybatisOutboxPublisher publisher = new MybatisOutboxPublisher(
                localMessageMapper, BizCodeRegistry.of("lead"));

        assertThrows(UnknownBizCodeException.class,
                () -> publisher.publish(new BusinessEvent(
                        "event-1", "lead-typo", "lead-1", "LEAD_ASSIGNED", "{}")));

        verifyNoInteractions(localMessageMapper);
    }
}
