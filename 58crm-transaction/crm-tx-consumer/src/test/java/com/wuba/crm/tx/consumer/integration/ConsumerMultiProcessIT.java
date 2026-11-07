package com.wuba.crm.tx.consumer.integration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ConsumerMultiProcessIT {

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
    private static final String STREAM_PREFIX = "tx:it:load-";

    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;
    private LettuceConnectionFactory redisFactory;
    private StringRedisTemplate redis;
    private final List<Process> processes = new ArrayList<>();
    private int streams;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(Boolean.getBoolean("tx.it"), "set -Dtx.it=true to run integration test");
        createDatabase();
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(MYSQL_URL);
        hikari.setUsername(MYSQL_USER);
        hikari.setPassword(MYSQL_PASSWORD);
        hikari.setMaximumPoolSize(4);
        hikari.setMinimumIdle(4);
        dataSource = new HikariDataSource(hikari);
        jdbc = new JdbcTemplate(dataSource);
        createTables();

        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(REDIS_HOST, REDIS_PORT);
        redisFactory = new LettuceConnectionFactory(redisConfig);
        redisFactory.afterPropertiesSet();
        redisFactory.start();
        redis = new StringRedisTemplate(redisFactory);
    }

    @AfterEach
    void tearDown() throws Exception {
        for (Process process : processes) {
            process.destroy();
        }
        for (Process process : processes) {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
        if (redis != null) {
            for (int i = 0; i < streams; i++) {
                redis.delete(STREAM_PREFIX + i);
            }
        }
        if (redisFactory != null) {
            redisFactory.destroy();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void throughput_multipleJvmProcesses() throws Exception {
        int count = Integer.getInteger("tx.load.count", 10_000);
        streams = Integer.getInteger("tx.load.streams", 4);
        int processesPerStream = Integer.getInteger("tx.load.processes", 1);
        int workers = Integer.getInteger("tx.load.workers", 8);
        int pool = Integer.getInteger("tx.load.pool", 8);
        int xaddBatch = Integer.getInteger("tx.load.xadd.batch", 500);

        for (int i = 0; i < streams; i++) {
            redis.delete(STREAM_PREFIX + i);
        }

        String classpath = System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path"));
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        CountDownLatch ready = new CountDownLatch(streams * processesPerStream);
        List<AtomicReference<String>> outputs = new ArrayList<>();

        for (int streamIndex = 0; streamIndex < streams; streamIndex++) {
            for (int instance = 0; instance < processesPerStream; instance++) {
                List<String> command = new ArrayList<>();
                command.add(java);
                command.add("-cp");
                command.add(classpath);
                command.add(ConsumerNodeMain.class.getName());
                command.add("mysqlUrl=" + MYSQL_URL);
                command.add("mysqlUser=" + MYSQL_USER);
                command.add("mysqlPassword=" + MYSQL_PASSWORD);
                command.add("redisHost=" + REDIS_HOST);
                command.add("redisPort=" + redisPort());
                command.add("streamPrefix=tx:it:");
                command.add("groupPrefix=tx-it-group-");
                command.add("bizCode=load-" + streamIndex);
                command.add("consumerId=load-" + streamIndex + "-" + instance);
                command.add("workers=" + workers);
                command.add("pool=" + pool);
                Process process = new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .start();
                processes.add(process);
                AtomicReference<String> output = new AtomicReference<>("");
                outputs.add(output);
                Thread reader = new Thread(() -> readOutput(process, ready, output));
                reader.setDaemon(true);
                reader.start();
            }
        }

        if (!ready.await(60, TimeUnit.SECONDS)) {
            throw new AssertionError("consumer nodes not ready: " + outputs);
        }

        long start = System.nanoTime();
        produce(count, streams, xaddBatch);
        waitConsumed(count, Duration.ofMinutes(10));
        long elapsedNanos = System.nanoTime() - start;
        double seconds = elapsedNanos / 1_000_000_000.0;
        double qps = count / seconds;
        System.out.printf("MULTIPROCESS_THROUGHPUT count=%d streams=%d processesPerStream=%d workers=%d elapsed=%.3fs qps=%.0f%n",
                count, streams, processesPerStream, workers, seconds, qps);
        assertTrue(qps > 0);
    }

    private void readOutput(Process process, CountDownLatch ready, AtomicReference<String> output) {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
                if (line.contains("NODE_READY")) {
                    ready.countDown();
                }
            }
        } catch (Exception ex) {
            builder.append("reader failed: ").append(ex).append('\n');
        } finally {
            output.set(builder.toString());
        }
    }

    private void produce(int count, int streams, int batchSize) {
        for (int offset = 0; offset < count; offset += batchSize) {
            final int batchStart = offset;
            final int batchEnd = Math.min(offset + batchSize, count);
            redis.executePipelined((RedisCallback<Object>) connection -> {
                for (int i = batchStart; i < batchEnd; i++) {
                    int streamIndex = i % streams;
                    Map<byte[], byte[]> fields = Map.of(
                            bytes("msgId"), bytes("load-" + streamIndex + "-" + i),
                            bytes("eventKey"), bytes("event-" + streamIndex + "-" + i),
                            bytes("bizCode"), bytes("load-" + streamIndex),
                            bytes("bizId"), bytes("biz-" + streamIndex + "-" + (i % 1000)),
                            bytes("eventType"), bytes("LOAD_EVENT"),
                            bytes("payload"), bytes("{}"));
                    connection.streamCommands().xAdd(bytes(STREAM_PREFIX + streamIndex), fields);
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
                    event_key VARCHAR(128),
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

    private int redisPort() {
        return REDIS_PORT;
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
