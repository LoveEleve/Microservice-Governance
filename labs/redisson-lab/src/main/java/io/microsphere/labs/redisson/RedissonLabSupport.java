package io.microsphere.labs.redisson;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

final class RedissonLabSupport {

    private RedissonLabSupport() {
    }

    static RedissonClient createClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:6379");
        return Redisson.create(config);
    }
}
