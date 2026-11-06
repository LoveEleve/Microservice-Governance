package io.microsphere.labs.spring.framework;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ContainerStartupTest {

    @Test
    void startsContainerAndInjectsDependency() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            OrderService service = context.getBean(OrderService.class);
            OrderService sameService = context.getBean(OrderService.class);

            assertSame(service, sameService);
            assertEquals("saved:order-1", service.createOrder("order-1"));
        }
    }
}
