package io.microsphere.labs.spring.framework;

import org.springframework.context.event.EventListener;

import java.util.concurrent.atomic.AtomicInteger;

public class OrderCreatedListener {

    private final AtomicInteger count = new AtomicInteger();

    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        count.incrementAndGet();
    }

    public int count() {
        return count.get();
    }
}
