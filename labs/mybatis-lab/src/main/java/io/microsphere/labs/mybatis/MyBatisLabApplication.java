package io.microsphere.labs.mybatis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("io.microsphere.labs.mybatis.mapper")
public class MyBatisLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyBatisLabApplication.class, args);
    }
}
