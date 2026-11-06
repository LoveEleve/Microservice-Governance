package io.microsphere.labs.spring.framework;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class TxService {

    private final JdbcTemplate jdbcTemplate;

    public TxService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void commit(String id) {
        jdbcTemplate.update("insert into tx_orders(id) values (?)", id);
    }

    @Transactional
    public void rollback(String id) {
        jdbcTemplate.update("insert into tx_orders(id) values (?)", id);
        throw new IllegalStateException("rollback");
    }

    public int count() {
        Integer value = jdbcTemplate.queryForObject(
                "select count(*) from tx_orders", Integer.class);
        return value == null ? 0 : value;
    }
}
