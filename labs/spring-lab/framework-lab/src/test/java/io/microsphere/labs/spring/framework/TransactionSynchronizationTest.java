package io.microsphere.labs.spring.framework;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionSynchronizationTest {

    @Test
    void runsCallbacksAroundCommit() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TxConfig.class)) {

            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            PlatformTransactionManager transactionManager =
                    context.getBean(PlatformTransactionManager.class);

            jdbcTemplate.execute("drop table if exists tx_callbacks");
            jdbcTemplate.execute("create table tx_callbacks(id varchar(32) primary key)");

            List<String> events = new ArrayList<>();
            TransactionTemplate transactionTemplate =
                    new TransactionTemplate(transactionManager);

            transactionTemplate.executeWithoutResult(status -> {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void beforeCommit(boolean readOnly) {
                                events.add("beforeCommit");
                            }

                            @Override
                            public void beforeCompletion() {
                                events.add("beforeCompletion");
                            }

                            @Override
                            public void afterCommit() {
                                events.add("afterCommit");
                            }

                            @Override
                            public void afterCompletion(int status) {
                                events.add("afterCompletion");
                            }
                        });
                jdbcTemplate.update("insert into tx_callbacks(id) values (?)", "tx-1");
            });

            assertEquals(
                    List.of("beforeCommit", "beforeCompletion", "afterCommit", "afterCompletion"),
                    events);
        }
    }
}
