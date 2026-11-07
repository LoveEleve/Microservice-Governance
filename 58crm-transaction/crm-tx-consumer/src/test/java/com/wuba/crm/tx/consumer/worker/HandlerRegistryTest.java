package com.wuba.crm.tx.consumer.worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandlerRegistryTest {

    @Test
    void handlerRegistry_resolvesByBizCodeAndEventType() {
        HandlerRegistry registry = new HandlerRegistry();
        MessageHandler handler = message -> {
        };
        registry.register("lead", "LEAD_ASSIGNED", handler);

        assertSame(handler, registry.require("lead", "LEAD_ASSIGNED"));
        assertTrue(registry.contains("lead", "LEAD_ASSIGNED"));
    }

    @Test
    void handlerRegistry_duplicateRegistrationFails() {
        HandlerRegistry registry = new HandlerRegistry();
        registry.register("lead", "LEAD_ASSIGNED", message -> {
        });

        assertThrows(IllegalStateException.class,
                () -> registry.register("lead", "LEAD_ASSIGNED", message -> {
                }));
    }

    @Test
    void handlerRegistry_unknownHandlerFails() {
        HandlerRegistry registry = new HandlerRegistry();

        assertThrows(UnknownHandlerException.class,
                () -> registry.require("lead", "LEAD_ASSIGNED"));
    }

    @Test
    void handlerRegistry_blankKeyFails() {
        HandlerRegistry registry = new HandlerRegistry();

        assertThrows(IllegalArgumentException.class,
                () -> registry.register(" ", "LEAD_ASSIGNED", message -> {
                }));
    }
}
