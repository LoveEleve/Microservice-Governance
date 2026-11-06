package com.wuba.crm.tx.outbox;

import java.util.List;

/**
 * 基于本地 Outbox 仓储的批量 ACK 实现。
 *
 * <p>ACK 只推进 SENT -> CONSUMED，不碰消费方的 lease/consumer_id。
 * 重复 ACK 是幂等的。</p>
 */
public class LocalMessageAckService implements MessageAckService {

    private final LocalMessageRepository repository;

    public LocalMessageAckService(LocalMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public int ackBatch(List<MessageAck> acks) {
        return repository.ackBatch(acks);
    }
}
