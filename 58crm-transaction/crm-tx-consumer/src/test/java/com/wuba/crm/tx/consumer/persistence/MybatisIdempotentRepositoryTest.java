package com.wuba.crm.tx.consumer.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MybatisIdempotentRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private DriverManagerDataSource dataSource;
    private org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:idempotent;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS t_idempotent");
        jdbc.execute("""
                CREATE TABLE t_idempotent (
                    msg_id VARCHAR(64) PRIMARY KEY,
                    biz_key VARCHAR(128) NOT NULL UNIQUE,
                    status VARCHAR(16) NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(IdempotentMapper.class);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        sqlSessionFactory = factoryBean.getObject();
    }

    @Test
    void tryAcquire_firstSucceeds_duplicateFails() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            MybatisIdempotentRepository repository = new MybatisIdempotentRepository(
                    session.getMapper(IdempotentMapper.class));

            assertTrue(repository.tryAcquire("m-1", "event-1", NOW));
            assertFalse(repository.tryAcquire("m-1", "event-1", NOW));

            Integer count = new JdbcTemplate(dataSource).queryForObject(
                    "SELECT COUNT(*) FROM t_idempotent", Integer.class);
            assertEquals(1, count);
        }
    }
}
