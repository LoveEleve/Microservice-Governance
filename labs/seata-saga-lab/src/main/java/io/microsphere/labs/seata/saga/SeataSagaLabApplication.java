package io.microsphere.labs.seata.saga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class SeataSagaLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeataSagaLabApplication.class, args);
    }
}
