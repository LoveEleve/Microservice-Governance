package com.wuba.crm.tx.outbox.pipeline.route;

import com.wuba.crm.tx.outbox.domain.OutboxRecord;

/**
 * 决定消息进入哪个业务线 Stream。
 *
 * <p>对应 Kafka 的 partitioner；本项目用 bizCode 作为路由维度。</p>
 */
public interface OutboxPartitioner {

    String route(OutboxRecord record);
}
