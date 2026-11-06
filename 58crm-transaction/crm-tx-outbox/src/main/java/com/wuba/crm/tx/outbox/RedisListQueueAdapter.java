package com.wuba.crm.tx.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
    public void enqueueBatch(String bizCode, List<QueueEnvelope> envelopes) {
        if (envelopes == null || envelopes.isEmpty()) {
            return;
        }
        List<String> payloads = new ArrayList<>(envelopes.size());
        for (QueueEnvelope envelope : envelopes) {
            try {
                payloads.add(objectMapper.writeValueAsString(envelope));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("serialize queue envelope failed", ex);
            }
        }
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) {
                ListOperations<String, String> list = operations.opsForList();
                for (String payload : payloads) {
                    list.leftPush(queueKey(bizCode), payload);
                }
                return null;
            }
        });
    }

    @Override
    public QueueEnvelope dequeue(String bizCode, Duration timeout) {
        Duration effectiveTimeout = timeout == null ? pollTimeout : timeout;
        String value = redisTemplate.opsForList().rightPop(
                queueKey(bizCode),
                effectiveTimeout.toMillis(),
                TimeUnit.MILLISECONDS);
        return decode(value);
    }

    @Override
    public List<QueueEnvelope> dequeueBatch(String bizCode, int maxCount, Duration timeout) {
        if (maxCount <= 0) {
            return List.of();
        }
        String key = queueKey(bizCode);
        List<String> values = redisTemplate.opsForList().rightPop(key, maxCount);
        if (values == null || values.isEmpty()) {
            Duration effectiveTimeout = timeout == null ? pollTimeout : timeout;
            String first = redisTemplate.opsForList().rightPop(
                    key,
                    effectiveTimeout.toMillis(),
                    TimeUnit.MILLISECONDS);
            if (first == null) {
                return List.of();
            }
            values = new ArrayList<>();
            values.add(first);
            if (maxCount > 1) {
                List<String> rest = redisTemplate.opsForList().rightPop(key, maxCount - 1);
                if (rest != null) {
                    values.addAll(rest);
                }
            }
        }
        List<QueueEnvelope> envelopes = new ArrayList<>(values.size());
        for (String value : values) {
            envelopes.add(decode(value));
        }
        return envelopes;
    }

    private QueueEnvelope decode(String value) {
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
