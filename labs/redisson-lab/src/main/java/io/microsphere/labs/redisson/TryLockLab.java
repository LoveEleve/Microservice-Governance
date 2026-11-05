package io.microsphere.labs.redisson;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

public final class TryLockLab {

    public static void main(String[] args) throws InterruptedException {
        RedissonClient client = RedissonLabSupport.createClient();
        RLock lock = client.getLock("lab:lock:try");
        try {
            boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!acquired) {
                System.out.println("busy: fallback / retry later");
                return;
            }
            try {
                System.out.println("acquired with wait=3s, lease=10s");
            } finally {
                lock.unlock();
            }
        } finally {
            client.shutdown();
        }
    }
}
