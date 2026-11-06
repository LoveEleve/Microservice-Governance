package com.wuba.crm.tx.outbox.pipeline.serializer;

import com.wuba.crm.tx.outbox.domain.OutboxRecord;

import java.util.Map;

/**
 * 把消息记录序列化为 Redis Stream 的 field/value。
 *
 * <p>可替换为加密、压缩或 schema 版本化实现。</p>
 */
public interface OutboxSerializer {

    Map<String, String> serialize(OutboxRecord record);
}
