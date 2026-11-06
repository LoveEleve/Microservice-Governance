package com.wuba.crm.tx.outbox.producer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把 bizCode 稳定地分配给 Sender shard。
 *
 * <p>规则：按 bizCode 排序后轮询分配；shard 数不能超过 bizCode 数，
 * 避免出现 8 个线程抢 2 个队列或空转线程。</p>
 */
public final class SenderAssignment {

    private SenderAssignment() {
    }

    public static Map<Integer, Set<String>> assign(Collection<String> bizCodes, int senderThreads) {
        if (bizCodes == null || bizCodes.isEmpty()) {
            throw new IllegalArgumentException("bizCodes must not be empty");
        }
        if (senderThreads <= 0) {
            throw new IllegalArgumentException("senderThreads must be positive");
        }

        List<String> sorted = new ArrayList<>(bizCodes);
        sorted.sort(String::compareTo);
        int shardCount = Math.min(senderThreads, sorted.size());

        Map<Integer, Set<String>> assignments = new LinkedHashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            int shard = i % shardCount;
            assignments.computeIfAbsent(shard, ignored -> new LinkedHashSet<>())
                    .add(sorted.get(i));
        }
        return assignments;
    }
}
