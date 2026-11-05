package io.microsphere.labs.redisson;

import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

public final class PermitExpirableLab {

    public static void main(String[] args) throws InterruptedException {
        RedissonClient client = RedissonLabSupport.createClient();
        String suffix = Long.toHexString(System.nanoTime());
        RPermitExpirableSemaphore semaphore =
                client.getPermitExpirableSemaphore("lab:permit:" + suffix);
        try {
            semaphore.trySetPermits(1);
            String permitId = semaphore.acquire(10, TimeUnit.SECONDS);
            try {
                System.out.println("permit acquired: " + permitId);
            } finally {
                semaphore.release(permitId);
            }
        } finally {
            client.shutdown();
        }
    }
}
