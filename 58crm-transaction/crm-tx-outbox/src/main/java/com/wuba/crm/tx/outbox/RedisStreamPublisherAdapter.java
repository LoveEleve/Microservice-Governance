package com.wuba.crm.tx.outbox;

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.stream.StreamRecords;

import java.util.List;
import java.util.Map;

/**
 * Spring Data Redis 的 Stream 写入适配器。
 *
 * <p>使用 pipeline 一次提交一批 XADD，避免每条消息一次网络往返。
 * Stream key 由调用方按 bizCode 生成。</p>
 */
public class RedisStreamPublisherAdapter implements RedisStreamPublisher {

    private final StringRedisTemplate redisTemplate;

    public RedisStreamPublisherAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void publish(String stream, List<Map<String, String>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) {
                for (Map<String, String> fields : records) {
                    operations.opsForStream().add(
                            StreamRecords.string(fields).withStreamKey(stream));
                }
                return null;
            }
        });
    }
}
