package com.wuba.crm.tx.outbox.producer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 线程数不能超过队列数；余数分给前几个 shard。
 */
class SenderAssignmentTest {

    @Test
    void assign_handlesNonDivisibleBizCodeCount() {
        Map<Integer, Set<String>> shards = SenderAssignment.assign(
                List.of("lead", "quota", "contract", "order", "customer"), 3);

        assertEquals(3, shards.size());
        assertEquals(5, shards.values().stream().mapToInt(Set::size).sum());
        assertEquals(Set.of("contract", "order"), shards.get(0));
    }

    @Test
    void assign_capsThreadCountAtBizCodeCount() {
        Map<Integer, Set<String>> shards = SenderAssignment.assign(
                List.of("lead", "quota"), 8);

        assertEquals(2, shards.size());
        assertTrue(shards.values().stream().allMatch(set -> set.size() == 1));
    }
}
