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
import com.wuba.crm.tx.common.MessageStatus;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 使用 H2 + MyBatis-Plus 验证真实 SQL 的状态迁移，而不是只 mock Mapper。
 */
class MybatisLocalMessageRepositoryTest {

    private DriverManagerDataSource dataSource;
    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:tx;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
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
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(LocalMessageMapper.class);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        sqlSessionFactory = factoryBean.getObject();
    }

    @Test
    void initialSend_marksSentWithoutIncrementingRetry() {
        insert("m-1", MessageStatus.INIT, 0);

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            LocalMessageMapper mapper = session.getMapper(LocalMessageMapper.class);
            MybatisLocalMessageRepository repository = new MybatisLocalMessageRepository(mapper);
            LocalMessage message = new LocalMessage(
                    "m-1", "event-m-1", "lead", "lead-1", "LEAD_ASSIGNED",
                    "{}", null, MessageStatus.INIT, 0);

            repository.markSentBatch(List.of(message), Instant.now().plusSeconds(30));

            LocalMessageEntity saved = mapper.selectById("m-1");
            assertEquals(MessageStatus.SENT.name(), saved.getStatus());
            assertEquals(0, saved.getRetryCount());
        }
    }

    @Test
    void retry_afterMaxAttempts_becomesDead() {
        insert("m-1", MessageStatus.INIT, 0);

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            LocalMessageMapper mapper = session.getMapper(LocalMessageMapper.class);
            MybatisLocalMessageRepository repository = new MybatisLocalMessageRepository(mapper);
            LocalMessage message = new LocalMessage(
                    "m-1", "event-m-1", "lead", "lead-1", "LEAD_ASSIGNED",
                    "{}", null, MessageStatus.INIT, 0);

            repository.markFailedBatch(List.of(message), Instant.now().plusSeconds(30), 1);

            LocalMessageEntity saved = mapper.selectById("m-1");
            assertEquals(MessageStatus.DEAD.name(), saved.getStatus());
            assertEquals(1, saved.getRetryCount());
        }
    }

    @Test
    void retry_belowMaxAttempts_staysInitAndIncrementsCount() {
        insert("m-1", MessageStatus.INIT, 0);

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            LocalMessageMapper mapper = session.getMapper(LocalMessageMapper.class);
            MybatisLocalMessageRepository repository = new MybatisLocalMessageRepository(mapper);
            LocalMessage message = new LocalMessage(
                    "m-1", "event-m-1", "lead", "lead-1", "LEAD_ASSIGNED",
                    "{}", null, MessageStatus.INIT, 0);

            repository.markFailedBatch(List.of(message), Instant.now().plusSeconds(30), 3);

            LocalMessageEntity saved = mapper.selectById("m-1");
            assertEquals(MessageStatus.INIT.name(), saved.getStatus());
            assertEquals(1, saved.getRetryCount());
        }
    }

    private void insert(String msgId, MessageStatus status, int retryCount) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO t_local_message
                    (msg_id, event_key, biz_code, biz_id, event_type, payload,
                     status, retry_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                msgId,
                "event-" + msgId,
                "lead",
                "lead-1",
                "LEAD_ASSIGNED",
                "{}",
                status.name(),
                retryCount,
                now,
                now);
    }
}
