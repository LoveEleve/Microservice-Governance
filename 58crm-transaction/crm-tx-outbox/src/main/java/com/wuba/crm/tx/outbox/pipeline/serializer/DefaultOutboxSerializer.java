package com.wuba.crm.tx.outbox.pipeline.serializer;

import com.wuba.crm.tx.outbox.domain.OutboxRecord;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认 Stream 字段序列化。
 */
public class DefaultOutboxSerializer implements OutboxSerializer {

    @Override
    public Map<String, String> serialize(OutboxRecord record) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("msgId", record.msgId());
        fields.put("eventKey", record.eventKey());
        fields.put("bizCode", record.bizCode());
        fields.put("bizId", record.bizId());
        fields.put("eventType", record.eventType());
        if (record.payload() != null) {
            fields.put("payload", record.payload());
        }
        if (record.payloadRef() != null) {
            fields.put("payloadRef", record.payloadRef());
        }
        return fields;
    }
}
