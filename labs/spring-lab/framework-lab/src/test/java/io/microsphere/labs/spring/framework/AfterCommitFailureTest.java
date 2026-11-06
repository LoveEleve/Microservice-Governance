package io.microsphere.labs.spring.framework;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AfterCommitFailureTest {

    @Test
    void afterCommitFailureDoesNotRollbackCommittedData() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TxConfig.class)) {

            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            PlatformTransactionManager transactionManager =
                    context.getBean(PlatformTransactionManager.class);

            jdbcTemplate.execute("drop table if exists after_commit_orders");
            jdbcTemplate.execute("create table after_commit_orders(id varchar(32) primary key)");

            List<String> events = new ArrayList<>();
            TransactionDefinition definition = new DefaultTransactionDefinition();
            TransactionStatus status = transactionManager.getTransaction(definition);

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            events.add("afterCommit");
                            throw new IllegalStateException("after commit failure");
                        }

                        @Override
                        public void afterCompletion(int status) {
                            events.add("afterCompletion");
                        }
                    });

            jdbcTemplate.update("insert into after_commit_orders(id) values (?)", "order-1");

            assertThrows(IllegalStateException.class, () -> transactionManager.commit(status));

            assertEquals(1, jdbcTemplate.queryForObject(
                    "select count(*) from after_commit_orders", Integer.class));
            assertEquals(List.of("afterCommit", "afterCompletion"), events);
        }
    }
}
