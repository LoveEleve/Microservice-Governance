package io.microsphere.labs.spring.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringJUnitConfig(TestConfig.class)
class SpringContextTest {

    @Autowired
    private GreetingService greetingService;

    @Test
    void injectsBeanFromTestContext() {
        assertEquals("hello spring", greetingService.greet("spring"));
    }
}
