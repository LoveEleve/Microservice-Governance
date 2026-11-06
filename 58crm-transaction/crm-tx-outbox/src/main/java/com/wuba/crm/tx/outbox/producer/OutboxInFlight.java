package com.wuba.crm.tx.outbox.producer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 已进入内存 buffer 或发送中的消息集合。
 *
 * <p>防止扫描线程在下一轮把同一条消息重复放入 buffer。进程崩溃后集合丢失，
 * MySQL 中的 INIT/SENT 仍会被重新扫描，重复由 Stream/消费方幂等兜底。</p>
 */
public class OutboxInFlight {

    // 只存 msgId，不存 payload；真正的消息仍在有界 buffer 中。
    private final Set<String> msgIds = ConcurrentHashMap.newKeySet();

    public boolean add(String msgId) {
        return msgIds.add(msgId);
    }

    public void remove(String msgId) {
        msgIds.remove(msgId);
    }

    public int size() {
        return msgIds.size();
    }
}
