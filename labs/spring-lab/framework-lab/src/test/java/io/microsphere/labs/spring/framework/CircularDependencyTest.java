package io.microsphere.labs.spring.framework;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertSame;

class CircularDependencyTest {

    @Test
    void resolvesSingletonFieldCircularDependency() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(CircularConfig.class)) {

            CircularA a = context.getBean(CircularA.class);
            CircularB b = context.getBean(CircularB.class);

            assertSame(a, b.circularA);
            assertSame(b, a.circularB);
        }
    }
}
