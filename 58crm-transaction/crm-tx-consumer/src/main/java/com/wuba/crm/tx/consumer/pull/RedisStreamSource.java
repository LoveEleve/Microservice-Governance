package com.wuba.crm.tx.consumer.pull;

import com.wuba.crm.tx.consumer.domain.ConsumedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RedisStreamSource implements StreamSource {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamSource.class);

    private final StringRedisTemplate redisTemplate;

    public RedisStreamSource(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<ConsumedMessage> read(
            String stream,
            String group,
            String consumer,
            int count,
            Duration block) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                Consumer.from(group, consumer),
                StreamReadOptions.empty().count(count).block(block),
                StreamOffset.create(stream, ReadOffset.lastConsumed()));
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<ConsumedMessage> messages = new ArrayList<>(records.size());
        for (MapRecord<String, Object, Object> record : records) {
            try {
                messages.add(StreamMessageDecoder.decode(record));
            } catch (IllegalArgumentException ex) {
                log.warn("skip invalid stream message, stream={}, entryId={}, reason={}",
                        stream, record.getId(), ex.getMessage());
            }
        }
        return messages;
    }
}
