package com.wuba.crm.tx.outbox.persistence;

import com.wuba.crm.tx.outbox.api.*;
import com.wuba.crm.tx.outbox.config.*;
import com.wuba.crm.tx.outbox.domain.*;
import com.wuba.crm.tx.outbox.persistence.*;
import com.wuba.crm.tx.outbox.producer.*;
import com.wuba.crm.tx.outbox.pipeline.interceptor.*;
import com.wuba.crm.tx.outbox.pipeline.route.*;
import com.wuba.crm.tx.outbox.pipeline.serializer.*;
import com.wuba.crm.tx.outbox.pipeline.stream.*;


import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 Spring 事务代理语义，而不是只验证 Mapper 逻辑。
 */
class MybatisOutboxPublisherTransactionTest {

    private AnnotationConfigApplicationContext context;
    private OutboxPublisher publisher;
    private TransactionTemplate transactionTemplate;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("DROP TABLE IF EXISTS t_local_message");
        jdbc.execute("""
                CREATE TABLE t_local_message (
                    msg_id VARCHAR(64) PRIMARY KEY,
                    event_key VARCHAR(128) NOT NULL UNIQUE,
                    biz_code VARCHAR(32) NOT NULL,
                    biz_id VARCHAR(64) NOT NULL,
                    event_type VARCHAR(64) NOT NULL,
                    payload CLOB,
                    payload_ref VARCHAR(256),
                    status VARCHAR(16) NOT NULL,
                    retry_count INT NOT NULL DEFAULT 0,
                    next_retry_at TIMESTAMP,
                    stream_entry_id VARCHAR(64),
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        publisher = context.getBean(OutboxPublisher.class);
        transactionTemplate = new TransactionTemplate(
                context.getBean(PlatformTransactionManager.class));
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void publish_withoutOuterTransaction_isRejected() {
        assertThrows(IllegalTransactionStateException.class,
                () -> publisher.publish(event("m-1")));

        assertEquals(0, countMessages());
    }

    @Test
    void publish_rollsBackWithBusinessFailure() {
        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            publisher.publish(event("m-1"));
            throw new IllegalStateException("business failed");
        }));

        assertEquals(0, countMessages());
    }

    @Test
    void publish_commitsWithBusinessTransaction() {
        transactionTemplate.executeWithoutResult(status -> publisher.publish(event("m-1")));

        assertEquals(1, countMessages());
    }

    private BusinessEvent event(String msgId) {
        return new BusinessEvent(
                "event-" + msgId,
                "lead",
                "lead-1",
                "LEAD_ASSIGNED",
                "{}");
    }

    private int countMessages() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM t_local_message", Integer.class);
        return count == null ? 0 : count;
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:publisher;MODE=MySQL;DB_CLOSE_DELAY=-1",
                    "sa",
                    "");
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.addMapper(LocalMessageMapper.class);
            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setConfiguration(configuration);
            return factoryBean.getObject();
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory);
        }

        @Bean
        LocalMessageMapper localMessageMapper(SqlSessionTemplate sqlSessionTemplate) {
            return sqlSessionTemplate.getMapper(LocalMessageMapper.class);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        MybatisOutboxPublisher mybatisOutboxPublisher(LocalMessageMapper mapper) {
            return new MybatisOutboxPublisher(mapper, BizCodeRegistry.of("lead"));
        }
    }
}
