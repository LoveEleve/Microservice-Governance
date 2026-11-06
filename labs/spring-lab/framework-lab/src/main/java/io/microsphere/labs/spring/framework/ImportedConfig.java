package io.microsphere.labs.spring.framework;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ImportedConfig {

    @Bean
    public ImportedService importedService() {
        return new ImportedService();
    }
}
