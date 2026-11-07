package com.wuba.crm.tx.consumer.pull;

import com.wuba.crm.tx.consumer.domain.ConsumedMessage;
import org.springframework.data.redis.connection.stream.MapRecord;

import java.util.Map;

public final class StreamMessageDecoder {

    private StreamMessageDecoder() {
    }

    public static ConsumedMessage decode(MapRecord<String, Object, Object> record) {
        Map<Object, Object> fields = record.getValue();
        return new ConsumedMessage(
                text(fields, "msgId"),
                text(fields, "eventKey"),
                text(fields, "bizCode"),
                text(fields, "bizId"),
                text(fields, "eventType"),
                record.getId().getValue(),
                text(fields, "payload"),
                text(fields, "payloadRef"));
    }

    private static String text(Map<Object, Object> fields, String name) {
        Object value = fields.get(name);
        return value == null ? null : value.toString();
    }
}
