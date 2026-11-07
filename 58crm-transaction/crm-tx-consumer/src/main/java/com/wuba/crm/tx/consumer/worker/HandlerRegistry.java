package com.wuba.crm.tx.consumer.worker;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class HandlerRegistry {

    private final Map<HandlerKey, MessageHandler> handlers = new ConcurrentHashMap<>();

    public void register(String bizCode, String eventType, MessageHandler handler) {
        HandlerKey key = new HandlerKey(bizCode, eventType);
        MessageHandler existing = handlers.putIfAbsent(key, Objects.requireNonNull(handler, "handler"));
        if (existing != null) {
            throw new IllegalStateException(
                    "duplicate consumer handler for bizCode=" + bizCode + ", eventType=" + eventType);
        }
    }

    public MessageHandler require(String bizCode, String eventType) {
        MessageHandler handler = handlers.get(new HandlerKey(bizCode, eventType));
        if (handler == null) {
            throw new UnknownHandlerException(bizCode, eventType);
        }
        return handler;
    }

    public boolean contains(String bizCode, String eventType) {
        return handlers.containsKey(new HandlerKey(bizCode, eventType));
    }

    public int size() {
        return handlers.size();
    }

    private record HandlerKey(String bizCode, String eventType) {

        private HandlerKey {
            requireText(bizCode, "bizCode");
            requireText(eventType, "eventType");
        }

        private static void requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " is required");
            }
        }
    }
}
