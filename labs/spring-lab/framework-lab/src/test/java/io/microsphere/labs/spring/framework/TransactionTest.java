package io.microsphere.labs.spring.framework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionTest {

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TxConfig.class);
        jdbcTemplate = context.getBean(JdbcTemplate.class);
        jdbcTemplate.execute("drop table if exists tx_orders");
        jdbcTemplate.execute("create table tx_orders(id varchar(32) primary key)");
    }

    @Test
    void commitsOnSuccess() {
        context.getBean(TxService.class).commit("commit-1");
        assertEquals(1, context.getBean(TxService.class).count());
        context.close();
    }

    @Test
    void rollsBackOnRuntimeException() {
        TxService service = context.getBean(TxService.class);
        assertThrows(IllegalStateException.class, () -> service.rollback("rollback-1"));
        assertEquals(0, service.count());
        context.close();
    }
}
