package io.microsphere.labs.seata.tcc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class SeataTccLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeataTccLabApplication.class, args);
    }
}
