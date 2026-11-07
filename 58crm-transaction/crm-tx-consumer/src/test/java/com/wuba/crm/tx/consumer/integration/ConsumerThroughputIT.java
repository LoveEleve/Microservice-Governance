package com.wuba.crm.tx.consumer.integration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.wuba.crm.tx.consumer.bootstrap.ConsumerBootstrap;
import com.wuba.crm.tx.consumer.bootstrap.ConsumerRuntimeConfig;
import com.wuba.crm.tx.consumer.persistence.ConsumerRecordMapper;
import com.wuba.crm.tx.consumer.persistence.IdempotentMapper;
import com.wuba.crm.tx.consumer.persistence.MybatisConsumerRecordRepository;
import com.wuba.crm.tx.consumer.persistence.MybatisIdempotentRepository;
import com.wuba.crm.tx.consumer.worker.HandlerRegistry;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ConsumerThroughputIT {

    private static final String MYSQL_HOST = System.getProperty("tx.mysql.host", "127.0.0.1");
    private static final String MYSQL_PORT = System.getProperty("tx.mysql.port", "3306");
    private static final String MYSQL_USER = System.getProperty("tx.mysql.user", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("tx.mysql.password", "");
    private static final String MYSQL_URL = "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT
            + "/crm_tx?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String MYSQL_SERVER_URL = "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT
            + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String REDIS_HOST = System.getProperty("tx.redis.host", "127.0.0.1");
    private static final int REDIS_PORT = Integer.parseInt(System.getProperty("tx.redis.port", "6379"));
    private static final String STREAM = "tx:it:load";
    private static final String GROUP = "tx-it-group-load";

    private LettuceConnectionFactory redisFactory;
    private StringRedisTemplate redis;
    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;
    private final List<ConsumerBootstrap> bootstraps = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(Boolean.getBoolean("tx.it"), "set -Dtx.it=true to run integration test");
        createDatabase();
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(MYSQL_URL);
        hikari.setUsername(MYSQL_USER);
        hikari.setPassword(MYSQL_PASSWORD);
        int poolSize = Integer.getInteger("tx.load.pool", 64);
        hikari.setMaximumPoolSize(poolSize);
        hikari.setMinimumIdle(poolSize);
        dataSource = new HikariDataSource(hikari);
        jdbc = new JdbcTemplate(dataSource);
        createTables();

        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(REDIS_HOST, REDIS_PORT);
        redisFactory = new LettuceConnectionFactory(redisConfig);
        redisFactory.afterPropertiesSet();
        redisFactory.start();
        redis = new StringRedisTemplate(redisFactory);
        redis.delete(STREAM);
    }

    @AfterEach
    void tearDown() {
        for (ConsumerBootstrap bootstrap : bootstraps) {
            bootstrap.close();
        }
        if (redis != null) {
            redis.delete(STREAM);
        }
        if (redisFactory != null) {
            redisFactory.destroy();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void throughput_endToEnd() throws Exception {
        int count = Integer.getInteger("tx.load.count", 10_000);
        int xaddBatch = Integer.getInteger("tx.load.xadd.batch", 500);
        produce(count, xaddBatch);

        SqlSessionFactory sqlSessionFactory = sqlSessionFactory(dataSource);
        SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
        MybatisConsumerRecordRepository repository = new MybatisConsumerRecordRepository(
                sqlSessionTemplate.getMapper(ConsumerRecordMapper.class));
        MybatisIdempotentRepository idempotent = new MybatisIdempotentRepository(
                sqlSessionTemplate.getMapper(IdempotentMapper.class));
        HandlerRegistry handlers = new HandlerRegistry();
        AtomicLong handled = new AtomicLong();
        handlers.register("load", "LOAD_EVENT", message -> handled.incrementAndGet());
        TransactionTemplate transactions = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));

        int instances = Integer.getInteger("tx.load.instances", 1);
        int workersPerInstance = Integer.getInteger("tx.load.workers", 16);
        String baseId = "load-consumer-" + UUID.randomUUID();
        for (int i = 0; i < instances; i++) {
            ConsumerRuntimeConfig config = new ConsumerRuntimeConfig(
                    "load", baseId + "-" + i,
                    "tx:it:", "tx-it-group-",
                    50_000, 500, Duration.ofMillis(100),
                    500, workersPerInstance,
                    Duration.ofSeconds(60), Duration.ofMinutes(5),
                    Duration.ofSeconds(30), 3, Duration.ofMillis(10), Duration.ofSeconds(20),
                    500, 100_000, Duration.ofMillis(100),
                    100, Duration.ofSeconds(30), 500, Duration.ofSeconds(1));
            bootstraps.add(new ConsumerBootstrap(
                    config, repository, idempotent, handlers, transactions, redis));
        }

        long start = System.nanoTime();
        for (ConsumerBootstrap bootstrap : bootstraps) {
            bootstrap.start();
        }
        waitConsumed(count, Duration.ofMinutes(10));
        long elapsedNanos = System.nanoTime() - start;

        double seconds = elapsedNanos / 1_000_000_000.0;
        double qps = count / seconds;
        System.out.printf("CONSUMER_THROUGHPUT count=%d attempts=%d instances=%d workersPerInstance=%d elapsed=%.3fs qps=%.0f%n",
                count, handled.get(), instances, workersPerInstance, seconds, qps);
        assertTrue(handled.get() >= count);
    }

    private void produce(int count, int batchSize) {
        for (int offset = 0; offset < count; offset += batchSize) {
            final int batchStart = offset;
            final int batchEnd = Math.min(offset + batchSize, count);
            redis.executePipelined((RedisCallback<Object>) connection -> {
                for (int i = batchStart; i < batchEnd; i++) {
                    Map<byte[], byte[]> fields = Map.of(
                            bytes("msgId"), bytes("load-" + i),
                            bytes("bizCode"), bytes("load"),
                            bytes("bizId"), bytes("biz-" + (i % 1000)),
                            bytes("eventType"), bytes("LOAD_EVENT"),
                            bytes("payload"), bytes("{}"));
                    connection.streamCommands().xAdd(bytes(STREAM), fields);
                }
                return null;
            });
        }
    }

    private void waitConsumed(int expected, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Integer consumed = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_consume_record WHERE status='CONSUMED'", Integer.class);
            if (consumed != null && consumed >= expected) {
                return;
            }
            Thread.sleep(100);
        }
        Integer consumed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_consume_record WHERE status='CONSUMED'", Integer.class);
        throw new AssertionError("timeout waiting for consumed, expected=" + expected
                + ", actual=" + consumed);
    }

    private SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(ConsumerRecordMapper.class);
        configuration.addMapper(IdempotentMapper.class);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        return factoryBean.getObject();
    }

    private void createDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL_SERVER_URL, MYSQL_USER, MYSQL_PASSWORD)) {
            connection.createStatement().execute(
                    "CREATE DATABASE IF NOT EXISTS crm_tx CHARACTER SET utf8mb4");
        }
    }

    private void createTables() {
        jdbc.execute("DROP TABLE IF EXISTS t_consume_record");
        jdbc.execute("""
                CREATE TABLE t_consume_record (
                    msg_id VARCHAR(64) PRIMARY KEY,
                    biz_code VARCHAR(32) NOT NULL,
                    biz_id VARCHAR(64) NOT NULL,
                    event_type VARCHAR(64) NOT NULL,
                    stream_entry_id VARCHAR(64) NOT NULL,
                    consumer_id VARCHAR(64),
                    status VARCHAR(16) NOT NULL,
                    owner_token VARCHAR(64),
                    owner_epoch BIGINT NOT NULL DEFAULT 0,
                    claim_count INT NOT NULL DEFAULT 0,
                    lease_until DATETIME,
                    retry_count INT NOT NULL DEFAULT 0,
                    next_retry_at DATETIME,
                    payload MEDIUMTEXT,
                    payload_ref VARCHAR(256),
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    KEY idx_status_lease (status, lease_until),
                    KEY idx_biz_claim (biz_code, status, next_retry_at, created_at),
                    KEY idx_owner_token (owner_token, status),
                    KEY idx_stream_entry (stream_entry_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbc.execute("DROP TABLE IF EXISTS t_idempotent");
        jdbc.execute("""
                CREATE TABLE t_idempotent (
                    msg_id VARCHAR(64) PRIMARY KEY,
                    biz_key VARCHAR(128) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    created_at DATETIME NOT NULL,
                    UNIQUE KEY uk_biz_key (biz_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
