package io.microsphere.labs.redisson;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

public final class MultiLockLab {

    public static void main(String[] args) {
        RedissonClient client = RedissonLabSupport.createClient();
        try {
            RLock lock1 = client.getLock("lab:multi:1");
            RLock lock2 = client.getLock("lab:multi:2");
            RLock multi = client.getMultiLock(lock1, lock2);
            multi.lock();
            try {
                System.out.println("multi lock acquired: " + multi.getName());
            } finally {
                multi.unlock();
            }
        } finally {
            client.shutdown();
        }
    }
}
