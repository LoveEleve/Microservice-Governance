package com.wuba.crm.tx.consumer.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisStreamGroupInitializerTest {

    @Test
    void ensureGroup_executesCreateGroup() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisCallback.class))).thenReturn("0-0");
        RedisStreamGroupInitializer initializer = new RedisStreamGroupInitializer(redis);

        initializer.ensureGroup("tx:stream:lead", "tx-group-lead");

        verify(redis).execute(any(RedisCallback.class));
    }

    @Test
    void busyGroup_isIgnored() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisCallback.class)))
                .thenThrow(new DataAccessResourceFailureException("BUSYGROUP"));
        RedisStreamGroupInitializer initializer = new RedisStreamGroupInitializer(redis);

        initializer.ensureGroup("tx:stream:lead", "tx-group-lead");
    }
}
