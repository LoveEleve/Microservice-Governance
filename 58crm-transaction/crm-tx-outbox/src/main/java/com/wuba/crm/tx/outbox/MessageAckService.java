package com.wuba.crm.tx.outbox;

import java.util.List;

/**
 * 生产方接收消费方批量回执的入口。
 *
 * <p>业务方不直接调用；消费方处理成功后按批调用，避免每条消息一次 RPC。</p>
 */
public interface MessageAckService {

    /**
     * 批量确认消费成功。
     *
     * @return 实际推进的消息数；重复 ACK 返回 0 也算成功
     */
    int ackBatch(List<MessageAck> acks);
}
