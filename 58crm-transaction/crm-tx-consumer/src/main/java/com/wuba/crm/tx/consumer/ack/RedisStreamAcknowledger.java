package com.wuba.crm.tx.consumer.ack;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Objects;

public class RedisStreamAcknowledger implements StreamAcknowledger {

    private final StringRedisTemplate redisTemplate;

    public RedisStreamAcknowledger(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
    }

    @Override
    public long acknowledge(String stream, String group, String... entryIds) {
        Long acknowledged = redisTemplate.opsForStream().acknowledge(stream, group, entryIds);
        return acknowledged == null ? 0 : acknowledged;
    }
}
