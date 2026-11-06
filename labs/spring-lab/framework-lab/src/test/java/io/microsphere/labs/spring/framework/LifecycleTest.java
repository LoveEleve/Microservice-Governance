package io.microsphere.labs.spring.framework;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LifecycleTest {

    @Test
    void runsInitAndDestroyCallbacksInOrder() {
        LifecycleBean.EVENTS.clear();
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(LifecycleConfig.class)) {
            context.getBean(LifecycleBean.class);
        }

        assertEquals(
                List.of("afterPropertiesSet", "customInit", "destroy", "customDestroy"),
                LifecycleBean.EVENTS);
    }
}
