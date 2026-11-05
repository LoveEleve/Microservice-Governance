package io.microsphere.labs.redisson;

import org.redisson.api.RFencedLock;
import org.redisson.api.RedissonClient;

public final class FencedLockLab {

    public static void main(String[] args) {
        RedissonClient client = RedissonLabSupport.createClient();
        RFencedLock lock = client.getFencedLock("lab:fence:order");
        try {
            Long token = lock.lockAndGetToken();
            try {
                // 下游写路径应校验 token：fence < token 时拒绝
                System.out.println("fencing token=" + token);
            } finally {
                lock.unlock();
            }
        } finally {
            client.shutdown();
        }
    }
}
