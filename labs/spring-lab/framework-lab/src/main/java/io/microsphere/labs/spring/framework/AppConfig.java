package io.microsphere.labs.spring.framework;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public OrderRepository orderRepository() {
        return new OrderRepository();
    }

    @Bean
    public OrderService orderService(OrderRepository orderRepository) {
        return new OrderService(orderRepository);
    }
}
