package io.microsphere.labs.spring.framework;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventTest {

    @Test
    void publishesEventToEventListener() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(EventConfig.class)) {

            OrderCreatedListener listener =
                    context.getBean(OrderCreatedListener.class);

            context.publishEvent(new OrderCreatedEvent(this, "order-1"));

            assertEquals(1, listener.count());
        }
    }
}
