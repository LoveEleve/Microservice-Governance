package com.wuba.crm.tx.tcc.id;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 TCC XID 始终是正数且单调递增。
 */
class TccXidGeneratorTest {

    @Test
    void nextXid_isPositiveAndIncreasing() {
        SegmentIdGenerator segmentIdGenerator = new SegmentIdGenerator(
                10, 10_000, 60_000, 0.9, new InMemorySegmentAllocator());
        try {
            TccXidGenerator generator = new TccXidGenerator(segmentIdGenerator);
            long previous = -1;
            for (int i = 0; i < 100; i++) {
                long xid = generator.nextXid();
                assertTrue(xid > 0, "xid must be positive at " + i);
                assertTrue(xid > previous, "xid is not increasing at " + i);
                previous = xid;
            }
            assertEquals(1L, previous - 99);
        } finally {
            segmentIdGenerator.close();
        }
    }
}
