package io.microsphere.labs.spring.framework;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventConfig {

    @Bean
    public OrderCreatedListener orderCreatedListener() {
        return new OrderCreatedListener();
    }
}
