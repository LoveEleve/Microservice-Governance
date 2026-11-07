package com.wuba.crm.tx.consumer.lease;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InFlightRegistryTest {

    @Test
    void registerAndUnregister_trackByOwnerToken() {
        InFlightRegistry registry = new InFlightRegistry();
        LeaseTask task = task("m-1", "owner-1");

        registry.register(task);

        assertTrue(registry.contains(task));
        assertEquals(1, registry.size());

        registry.unregister(task);

        assertFalse(registry.contains(task));
        assertEquals(0, registry.size());
    }

    @Test
    void duplicateOwner_throws() {
        InFlightRegistry registry = new InFlightRegistry();
        registry.register(task("m-1", "owner-1"));

        assertThrows(IllegalStateException.class,
                () -> registry.register(task("m-1", "owner-1")));
    }

    @Test
    void differentOwner_sameMessageCanCoexist() {
        InFlightRegistry registry = new InFlightRegistry();
        LeaseTask first = task("m-1", "owner-1");
        LeaseTask second = task("m-1", "owner-2");

        registry.register(first);
        registry.register(second);

        assertEquals(2, registry.size());
        assertEquals(2, registry.snapshot().size());
        assertTrue(registry.snapshot().containsAll(List.of(first, second)));
    }

    private LeaseTask task(String msgId, String ownerToken) {
        return new LeaseTask(msgId, "lead", "c-1", ownerToken, 1,
                Instant.parse("2026-01-01T01:00:00Z"));
    }
}
