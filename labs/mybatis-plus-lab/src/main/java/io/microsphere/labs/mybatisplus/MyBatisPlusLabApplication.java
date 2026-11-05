package io.microsphere.labs.mybatisplus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("io.microsphere.labs.mybatisplus.mapper")
public class MyBatisPlusLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyBatisPlusLabApplication.class, args);
    }
}
