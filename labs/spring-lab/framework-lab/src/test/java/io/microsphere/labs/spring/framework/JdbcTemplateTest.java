package io.microsphere.labs.spring.framework;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcTemplateTest {

    @Test
    void reusesTransactionalConnectionAndTranslatesEmptyResult() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TxConfig.class)) {

            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            DataSource dataSource = context.getBean(DataSource.class);
            PlatformTransactionManager transactionManager =
                    context.getBean(PlatformTransactionManager.class);

            jdbcTemplate.execute("drop table if exists jdbc_orders");
            jdbcTemplate.execute("create table jdbc_orders(id varchar(32) primary key)");

            TransactionTemplate transactionTemplate =
                    new TransactionTemplate(transactionManager);

            transactionTemplate.executeWithoutResult(status -> {
                Connection first = DataSourceUtils.getConnection(dataSource);
                Connection second = DataSourceUtils.getConnection(dataSource);
                try {
                    assertSame(first, second);
                } finally {
                    DataSourceUtils.releaseConnection(first, dataSource);
                    DataSourceUtils.releaseConnection(second, dataSource);
                }
            });

            assertThrows(EmptyResultDataAccessException.class,
                    () -> jdbcTemplate.queryForObject(
                            "select id from jdbc_orders where id = ?",
                            String.class, "missing"));

            assertEquals(0, jdbcTemplate.queryForObject(
                    "select count(*) from jdbc_orders", Integer.class));
        }
    }
}
