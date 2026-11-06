package io.microsphere.labs.spring.framework;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(NeverCondition.class)
public class ConditionalConfig {

    @Bean
    public ImportedService conditionalService() {
        return new ImportedService();
    }
}
