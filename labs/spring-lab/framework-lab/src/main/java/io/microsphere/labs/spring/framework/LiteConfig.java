package io.microsphere.labs.spring.framework;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LiteConfig {

    @Bean
    public AnnotationA annotationA() {
        return new AnnotationA();
    }

    @Bean
    public AnnotationB annotationB() {
        return new AnnotationB(annotationA());
    }
}
