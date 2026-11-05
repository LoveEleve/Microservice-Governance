package io.microsphere.labs.redisson;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

public final class SpinLockLab {

    public static void main(String[] args) {
        RedissonClient client = RedissonLabSupport.createClient();
        RLock lock = client.getSpinLock("lab:lock:spin");
        try {
            lock.lock();
            try {
                System.out.println("spin lock acquired");
            } finally {
                lock.unlock();
            }
        } finally {
            client.shutdown();
        }
    }
}
