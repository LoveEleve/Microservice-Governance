package com.wuba.crm.tx.outbox.pipeline.route;

import com.wuba.crm.tx.outbox.domain.OutboxRecord;

/**
 * 默认按 bizCode 路由。每个业务线一个 Stream，不做多下游 fan-out。
 */
public class BizCodePartitioner implements OutboxPartitioner {

    @Override
    public String route(OutboxRecord record) {
        return record.bizCode();
    }
}
