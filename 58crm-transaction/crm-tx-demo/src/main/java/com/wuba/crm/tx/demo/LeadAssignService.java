package com.wuba.crm.tx.demo;

import com.wuba.crm.tx.outbox.api.BusinessEvent;
import com.wuba.crm.tx.outbox.api.OutboxPublisher;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务方示例：线索分配。
 *
 * <p>业务方只负责两件事：写业务表、发布事件。  
 * 事件写入 MySQL 的 t_local_message，后续由 Scanner/Sender 发到 Redis Stream。</p>
 */
public class LeadAssignService {

    private final LeadRepository leadRepository;
    private final OutboxPublisher outboxPublisher;

    public LeadAssignService(LeadRepository leadRepository, OutboxPublisher outboxPublisher) {
        this.leadRepository = leadRepository;
        this.outboxPublisher = outboxPublisher;
    }

    /**
     * 分配线索。
     *
     * <p>@Transactional 必须在最外层业务方法上；publish 内部要求 MANDATORY 事务。</p>
     */
    @Transactional
    public void assign(AssignLeadCommand command) {
        // 1. 业务表更新：把 lead 归属改成 salesId。
        leadRepository.assign(command.leadId(), command.salesId());

        // 2. 同一事务写 Outbox 事件；业务回滚时这条消息也会回滚。
        outboxPublisher.publish(new BusinessEvent(
                "lead-assigned:" + command.leadId() + ":" + command.requestId(),
                "lead",
                String.valueOf(command.leadId()),
                "LEAD_ASSIGNED",
                "{\"leadId\":" + command.leadId()
                        + ",\"salesId\":" + command.salesId() + "}"
        ));
    }
}
