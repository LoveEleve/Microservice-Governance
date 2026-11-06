package com.wuba.crm.tx.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis List 的队列适配器。
 *
 * <p>key 固定为 {@code tx:queue:{bizCode}}；入队使用 LPUSH，出队使用
 * BRPOP 的阻塞版本。这里不引入消费者组，也不做 fan-out。</p>
 */
public class RedisListQueueAdapter implements RedisListQueue {

    private static final String QUEUE_PREFIX = "tx:queue:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration pollTimeout;

    public RedisListQueueAdapter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Duration pollTimeout) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.pollTimeout = pollTimeout;
    }

    @Override
    public void enqueue(String bizCode, QueueEnvelope envelope) {
        try {
            redisTemplate.opsForList().leftPush(
                    queueKey(bizCode),
                    objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("serialize queue envelope failed", ex);
        }
    }

    @Override
    public QueueEnvelope dequeue(String bizCode, Duration timeout) {
        Duration effectiveTimeout = timeout == null ? pollTimeout : timeout;
        String value = redisTemplate.opsForList().rightPop(
                queueKey(bizCode),
                effectiveTimeout.toMillis(),
                TimeUnit.MILLISECONDS);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, QueueEnvelope.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("deserialize queue envelope failed", ex);
        }
    }

    private String queueKey(String bizCode) {
        return QUEUE_PREFIX + bizCode;
    }
}
