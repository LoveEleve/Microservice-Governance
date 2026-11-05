package io.microsphere.labs.redisson;

import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;

public final class FairReadWriteLab {

    public static void main(String[] args) {
        RedissonClient client = RedissonLabSupport.createClient();
        try {
            RLock fairLock = client.getFairLock("lab:lock:fair");
            fairLock.lock();
            try {
                System.out.println("fair lock acquired");
            } finally {
                fairLock.unlock();
            }

            RReadWriteLock rw = client.getReadWriteLock("lab:rw:config");
            RLock read = rw.readLock();
            read.lock();
            try {
                System.out.println("read lock acquired");
            } finally {
                read.unlock();
            }
        } finally {
            client.shutdown();
        }
    }
}
