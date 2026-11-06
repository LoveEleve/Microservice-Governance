package io.microsphere.labs.spring.framework;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CircularConfig {

    @Bean
    public CircularA circularA() {
        return new CircularA();
    }

    @Bean
    public CircularB circularB() {
        return new CircularB();
    }
}
