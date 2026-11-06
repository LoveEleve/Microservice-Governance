package io.microsphere.labs.spring.mvc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableWebMvc
public class WebConfig {

    @Bean
    public MvcLabController mvcLabController() {
        return new MvcLabController();
    }
}
