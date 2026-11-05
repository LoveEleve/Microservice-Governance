package io.microsphere.labs.redisson;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

public final class ReentrantLockLab {

    public static void main(String[] args) {
        RedissonClient client = RedissonLabSupport.createClient();
        RLock lock = client.getLock("lab:lock:reentrant");
        try {
            lock.lock();
            try {
                lock.lock();
                try {
                    System.out.println("holdCount=" + lock.getHoldCount());
                } finally {
                    lock.unlock();
                }
            } finally {
                lock.unlock();
            }
        } finally {
            client.shutdown();
        }
    }
}
