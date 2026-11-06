package io.microsphere.labs.spring.framework;

import org.springframework.beans.factory.annotation.Value;

public class EnvHolder {

    @Value("${app.name}")
    String name;

    @Value("${app.port}")
    int port;

    @Value("#{2 + 3}")
    int sum;
}
