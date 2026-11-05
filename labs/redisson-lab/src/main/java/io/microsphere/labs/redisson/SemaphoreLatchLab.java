package io.microsphere.labs.redisson;

import org.redisson.api.RCountDownLatch;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;

public final class SemaphoreLatchLab {

    public static void main(String[] args) throws InterruptedException {
        RedissonClient client = RedissonLabSupport.createClient();
        try {
            RSemaphore semaphore = client.getSemaphore("lab:sem:permits");
            semaphore.trySetPermits(2);
            semaphore.acquire();
            try {
                System.out.println("semaphore acquired");
            } finally {
                semaphore.release();
            }

            RCountDownLatch latch = client.getCountDownLatch("lab:latch");
            latch.trySetCount(1);
            latch.countDown();
            latch.await();
            System.out.println("latch released");
        } finally {
            client.shutdown();
        }
    }
}
