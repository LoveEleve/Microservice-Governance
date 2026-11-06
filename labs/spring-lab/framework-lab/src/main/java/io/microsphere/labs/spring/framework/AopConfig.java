package io.microsphere.labs.spring.framework;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
public class AopConfig {

    @Bean
    public AopService aopService() {
        return new AopService();
    }

    @Bean
    public CountingAspect countingAspect() {
        return new CountingAspect();
    }
}
