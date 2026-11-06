package io.microsphere.labs.spring.framework;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AopSelfInvocationTest {

    @Test
    void internalCallBypassesProxyButExternalCallDoesNot() {
        CountingAspect.reset();
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AopConfig.class)) {

            AopService service = context.getBean(AopService.class);

            service.work();
            assertEquals(0, CountingAspect.count());

            service.internal();
            assertEquals(1, CountingAspect.count());
        }
    }
}
