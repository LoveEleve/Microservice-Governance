package com.wuba.crm.tx.consumer.bootstrap;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class RedisStreamGroupInitializer {

    private final StringRedisTemplate redisTemplate;

    public RedisStreamGroupInitializer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
    }

    public void ensureGroup(String stream, String group) {
        try {
            redisTemplate.execute((RedisCallback<String>) connection ->
                    connection.streamCommands().xGroupCreate(
                            stream.getBytes(StandardCharsets.UTF_8),
                            group,
                            ReadOffset.from("0"),
                            true));
        } catch (DataAccessException ex) {
            if (!isBusyGroup(ex)) {
                throw ex;
            }
        }
    }

    private boolean isBusyGroup(DataAccessException ex) {
        Throwable cause = ex;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
