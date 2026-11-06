package com.wuba.crm.tx.outbox.integration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuba.crm.tx.outbox.api.BusinessEvent;
import com.wuba.crm.tx.outbox.api.OutboxPublisher;
import com.wuba.crm.tx.outbox.config.BizCodeRegistry;
import com.wuba.crm.tx.outbox.persistence.LocalMessageMapper;
import com.wuba.crm.tx.outbox.persistence.MybatisLocalMessageRepository;
import com.wuba.crm.tx.outbox.persistence.MybatisOutboxPublisher;
import com.wuba.crm.tx.outbox.pipeline.interceptor.OutboxInterceptorChain;
import com.wuba.crm.tx.outbox.pipeline.route.BizCodePartitioner;
import com.wuba.crm.tx.outbox.pipeline.serializer.DefaultOutboxSerializer;
import com.wuba.crm.tx.outbox.pipeline.stream.RedisStreamPublisherAdapter;
import com.wuba.crm.tx.outbox.producer.OutboxBuffer;
import com.wuba.crm.tx.outbox.producer.OutboxConfig;
import com.wuba.crm.tx.outbox.producer.OutboxInFlight;
import com.wuba.crm.tx.outbox.producer.OutboxScanner;
import com.wuba.crm.tx.outbox.producer.OutboxSender;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 真实 MySQL + Redis 7 验证：
 * t_local_message(INIT) -> Scanner -> Buffer -> Sender -> Redis Stream(SENT)。
 */
class RealStreamOutboxTest {

    private static final String MYSQL_HOST = System.getProperty("tx.mysql.host", "127.0.0.1");
    private static final String MYSQL_PORT = System.getProperty("tx.mysql.port", "3306");
    private static final String MYSQL_USER = System.getProperty("tx.mysql.user", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("tx.mysql.password", "");
    private static final String MYSQL_URL =
            "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT
                    + "/crm_tx?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String MYSQL_SERVER_URL =
            "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT
                    + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String REDIS_HOST = System.getProperty("tx.redis.host", "127.0.0.1");
    private static final int REDIS_PORT = Integer.parseInt(System.getProperty("tx.redis.port", "6379"));
    private static final String STREAM = "tx:it:lead";

    private AnnotationConfigApplicationContext context;
    private LettuceConnectionFactory redisFactory;
    private StringRedisTemplate redis;
    private OutboxPublisher publisher;
    private MybatisLocalMessageRepository repository;
    private TransactionTemplate transactionTemplate;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(Boolean.getBoolean("tx.it"), "set -Dtx.it=true to run real integration test");
        createDatabaseAndTable();
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        jdbc = context.getBean(JdbcTemplate.class);
        publisher = context.getBean(OutboxPublisher.class);
        repository = context.getBean(MybatisLocalMessageRepository.class);
        transactionTemplate = new TransactionTemplate(
                context.getBean(PlatformTransactionManager.class));

        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(REDIS_HOST, REDIS_PORT);
        redisFactory = new LettuceConnectionFactory(redisConfig);
        redisFactory.afterPropertiesSet();
        redisFactory.start();
        redis = new StringRedisTemplate(redisFactory);
        redis.delete(STREAM);
    }

    @AfterEach
    void tearDown() {
        if (redis != null) {
            redis.delete(STREAM);
        }
        if (redisFactory != null) {
            redisFactory.destroy();
        }
        if (context != null) {
            context.close();
        }
    }

    @Test
    void outboxToRedisStream_endToEnd() throws Exception {
        String eventKey = "event-" + UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> publisher.publish(
                new BusinessEvent(eventKey, "lead", "lead-1", "LEAD_ASSIGNED", "{}")));

        assertEquals("INIT", status());

        OutboxBuffer buffer = new OutboxBuffer(100, 1024 * 1024);
        OutboxInFlight inFlight = new OutboxInFlight();
        OutboxScanner scanner = new OutboxScanner(repository, buffer, inFlight, 100, BizCodeRegistry.of("lead"));
        assertEquals(1, scanner.scanOnce());

        OutboxConfig config = new OutboxConfig(
                "tx:it:", Duration.ofMillis(1), 10, 100, 1024 * 1024,
                100, Duration.ofMillis(10), Duration.ofSeconds(30), 3, 1);
        OutboxSender sender = new OutboxSender(
                buffer,
                new RedisStreamPublisherAdapter(redis),
                new OutboxInterceptorChain(List.of()),
                new BizCodePartitioner(),
                new DefaultOutboxSerializer(),
                repository,
                config,
                inFlight,
                Set.of("lead"));

        assertEquals(1, sender.sendOnce());
        assertEquals("SENT", status());
        assertEquals(1L, redis.opsForStream().size(STREAM));
    }

    private String status() {
        return jdbc.queryForObject(
                "SELECT status FROM t_local_message ORDER BY created_at LIMIT 1", String.class);
    }

    private void createDatabaseAndTable() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL_SERVER_URL, MYSQL_USER, MYSQL_PASSWORD)) {
            connection.createStatement().execute(
                    "CREATE DATABASE IF NOT EXISTS crm_tx CHARACTER SET utf8mb4");
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_local_message");
        jdbcTemplate.execute("""
                CREATE TABLE t_local_message (
                    msg_id VARCHAR(64) PRIMARY KEY,
                    event_key VARCHAR(128) NOT NULL UNIQUE,
                    biz_code VARCHAR(32) NOT NULL,
                    biz_id VARCHAR(64) NOT NULL,
                    event_type VARCHAR(64) NOT NULL,
                    payload MEDIUMTEXT,
                    payload_ref VARCHAR(256),
                    status VARCHAR(16) NOT NULL,
                    retry_count INT NOT NULL DEFAULT 0,
                    next_retry_at DATETIME,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    KEY idx_status_retry (status, next_retry_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
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
        MybatisLocalMessageRepository localMessageRepository(LocalMessageMapper mapper) {
            return new MybatisLocalMessageRepository(mapper);
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
