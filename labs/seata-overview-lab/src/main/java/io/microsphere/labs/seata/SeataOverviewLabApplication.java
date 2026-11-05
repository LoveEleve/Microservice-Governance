package io.microsphere.labs.seata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class SeataOverviewLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeataOverviewLabApplication.class, args);
    }
}
