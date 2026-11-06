package io.microsphere.labs.spring.framework;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.Resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentTest {

    @Test
    void resolvesPropertySourcePlaceholdersAndClasspathResource() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(EnvConfig.class)) {

            EnvHolder holder = context.getBean(EnvHolder.class);
            assertEquals("demo", holder.name);
            assertEquals(9090, holder.port);
            assertEquals(5, holder.sum);

            Resource resource = context.getResource("classpath:env.properties");
            assertTrue(resource.exists());
            assertEquals("demo", context.getEnvironment().getProperty("app.name"));
        }
    }
}
