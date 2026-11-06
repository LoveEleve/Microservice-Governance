package io.microsphere.labs.spring.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class BootLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootLabApplication.class, args);
    }

    @RestController
    public static class HelloController {

        @GetMapping("/hello")
        public String hello(@RequestParam(value = "name", defaultValue = "world") String name) {
            return "hello " + name;
        }
    }
}
