package io.microsphere.labs.spring.framework;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class AnnotationConfigTest {

    @Test
    void fullConfigInterceptsBeanMethodCall() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(FullConfig.class)) {
            AnnotationA a = context.getBean(AnnotationA.class);
            AnnotationB b = context.getBean(AnnotationB.class);
            assertSame(a, b.annotationA);
        }
    }

    @Test
    void liteConfigDoesNotInterceptBeanMethodCall() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(LiteConfig.class)) {
            AnnotationA a = context.getBean(AnnotationA.class);
            AnnotationB b = context.getBean(AnnotationB.class);
            assertNotSame(a, b.annotationA);
        }
    }

    @Test
    void importSelectorRegistersConfiguration() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ImportConfig.class)) {
            assertEquals(1, context.getBeansOfType(ImportedService.class).size());
        }
    }

    @Test
    void conditionCanSkipConfiguration() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ConditionalConfig.class)) {
            assertFalse(context.containsBean("conditionalService"));
        }
    }
}
