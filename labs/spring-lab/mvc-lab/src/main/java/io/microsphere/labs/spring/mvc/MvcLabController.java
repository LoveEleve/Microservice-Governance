package io.microsphere.labs.spring.mvc;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MvcLabController {

    @GetMapping("/hello")
    public String hello(@RequestParam(value = "name", defaultValue = "world") String name) {
        return "hello " + name;
    }
}
