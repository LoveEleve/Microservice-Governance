package io.microsphere.labs.redisson;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

public final class WatchdogLockLab {

    public static void main(String[] args) throws InterruptedException {
        RedissonClient client = RedissonLabSupport.createClient();
        RLock lock = client.getLock("lab:lock:watchdog");
        try {
            lock.lock();
            try {
                System.out.println("ttl before sleep=" + lock.remainTimeToLive());
                Thread.sleep(40_000);
                System.out.println("ttl after 40s=" + lock.remainTimeToLive());
            } finally {
                lock.unlock();
            }
        } finally {
            client.shutdown();
        }
    }
}
