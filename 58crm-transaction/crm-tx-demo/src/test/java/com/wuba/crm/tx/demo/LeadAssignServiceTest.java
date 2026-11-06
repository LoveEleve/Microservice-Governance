package com.wuba.crm.tx.demo;

import com.wuba.crm.tx.outbox.api.BusinessEvent;
import com.wuba.crm.tx.outbox.api.OutboxPublisher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 业务方最真实的接入方式：业务写库 + 同一事务发布事件。
 */
class LeadAssignServiceTest {

    @Test
    void assign_writesBusinessAndPublishesEvent() {
        FakeLeadRepository leadRepository = new FakeLeadRepository();
        FakeOutboxPublisher outboxPublisher = new FakeOutboxPublisher();
        LeadAssignService service = new LeadAssignService(leadRepository, outboxPublisher);

        service.assign(new AssignLeadCommand(1001L, 2002L, "req-001"));

        assertEquals(1001L, leadRepository.leadId);
        assertEquals(2002L, leadRepository.salesId);
        assertEquals(1, outboxPublisher.events.size());

        BusinessEvent event = outboxPublisher.events.get(0);
        assertEquals("lead-assigned:1001:req-001", event.eventKey());
        assertEquals("lead", event.bizCode());
        assertEquals("1001", event.bizId());
        assertEquals("LEAD_ASSIGNED", event.eventType());
    }

    private static final class FakeLeadRepository implements LeadRepository {

        private long leadId;
        private long salesId;

        @Override
        public void assign(long leadId, long salesId) {
            this.leadId = leadId;
            this.salesId = salesId;
        }
    }

    private static final class FakeOutboxPublisher implements OutboxPublisher {

        private final List<BusinessEvent> events = new ArrayList<>();

        @Override
        public String publish(BusinessEvent event) {
            events.add(event);
            return "msg-" + events.size();
        }
    }
}
