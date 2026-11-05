package io.microsphere.labs.redisson;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

public final class BasicLockLab {

    public static void main(String[] args) {
        RedissonClient client = RedissonLabSupport.createClient();
        RLock lock = client.getLock("lab:lock:order:1");
        try {
            lock.lock();
            try {
                System.out.println("critical section: " + Thread.currentThread().getName());
            } finally {
                lock.unlock();
            }
        } finally {
            client.shutdown();
        }
    }
}
