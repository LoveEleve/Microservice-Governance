package io.microsphere.labs.redisson;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

public final class WaitLockLab {

    public static void main(String[] args) throws InterruptedException {
        RedissonClient client = RedissonLabSupport.createClient();
        RLock lock = client.getLock("lab:lock:wait");
        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });
        try {
            holder.start();

            Thread.sleep(200);
            boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!acquired) {
                System.out.println("wait timeout");
                return;
            }
            try {
                System.out.println("acquired after waiting");
            } finally {
                lock.unlock();
            }
        } finally {
            holder.join(15_000);
            client.shutdown();
        }
    }
}
