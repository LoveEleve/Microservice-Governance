package com.wuba.crm.tx.consumer.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.wuba.crm.tx.consumer.domain.ClaimRequest;
import com.wuba.crm.tx.consumer.domain.ConsumeStatus;
import com.wuba.crm.tx.consumer.domain.ConsumedMessage;
import com.wuba.crm.tx.consumer.domain.ConsumerRecord;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MybatisConsumerRecordRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private DriverManagerDataSource dataSource;
    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:consumer;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
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
                    lease_until TIMESTAMP,
                    retry_count INT NOT NULL DEFAULT 0,
                    next_retry_at TIMESTAMP,
                    payload CLOB,
                    payload_ref VARCHAR(256),
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(ConsumerRecordMapper.class);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        sqlSessionFactory = factoryBean.getObject();
    }

    @Test
    void insertReadyBatch_insertsReadyRecords() throws Exception {
        withRepository(repository -> {
            assertEquals(1, repository.insertReadyBatch(List.of(message("m-1")), NOW));

            ConsumerRecordEntity saved = entity("m-1");
            assertEquals("READY", saved.getStatus());
            assertEquals(0, saved.getClaimCount());
            assertEquals(0, saved.getOwnerEpoch());
            assertEquals(null, saved.getOwnerToken());
        });
    }

    @Test
    void insertReadyBatch_duplicateMsgId_isIgnored() throws Exception {
        withRepository(repository -> {
            repository.insertReadyBatch(List.of(message("m-1")), NOW);
            ClaimRequest request = new ClaimRequest(
                    "lead", "c-1", "owner-1", 10, NOW, NOW.plusSeconds(60));
            repository.claimReadyBatch(request);
            repository.markConsumed("m-1", "owner-1", 1, NOW.plusSeconds(1));

            repository.insertReadyBatch(List.of(message("m-1")), NOW.plusSeconds(2));

            ConsumerRecordEntity saved = entity("m-1");
            assertEquals("CONSUMED", saved.getStatus());
            assertEquals(1, saved.getClaimCount());
        });
    }

    @Test
    void claimReadyBatch_movesReadyToProcessingWithEpoch() throws Exception {
        withRepository(repository -> {
            repository.insertReadyBatch(List.of(message("m-1")), NOW);
            ClaimRequest request = new ClaimRequest(
                    "lead", "c-1", "owner-1", 10, NOW, NOW.plusSeconds(60));

            List<ConsumerRecord> claimed = repository.claimReadyBatch(request);

            assertEquals(1, claimed.size());
            ConsumerRecord record = claimed.get(0);
            assertEquals(ConsumeStatus.PROCESSING, record.status());
            assertEquals("c-1", record.consumerId());
            assertEquals("owner-1", record.ownerToken());
            assertEquals(1, record.ownerEpoch());
            assertEquals(1, record.claimCount());
            assertEquals(NOW.plusSeconds(60), record.leaseUntil());
        });
    }

    @Test
    void claimReadyBatch_doesNotClaimBeforeNextRetryAt() throws Exception {
        withRepository(repository -> {
            repository.insertReadyBatch(List.of(message("m-1")), NOW);
            ClaimRequest claim = new ClaimRequest(
                    "lead", "c-1", "owner-1", 10, NOW, NOW.plusSeconds(60));
            repository.claimReadyBatch(claim);
            repository.markRetry("m-1", "owner-1", 1, NOW.plusSeconds(30), 3, NOW.plusSeconds(1));

            ClaimRequest early = new ClaimRequest(
                    "lead", "c-2", "owner-2", 10, NOW.plusSeconds(10), NOW.plusSeconds(70));
            assertTrue(repository.claimReadyBatch(early).isEmpty());

            ClaimRequest due = new ClaimRequest(
                    "lead", "c-2", "owner-2", 10, NOW.plusSeconds(31), NOW.plusSeconds(91));
            assertEquals(1, repository.claimReadyBatch(due).size());
        });
    }

    @Test
    void renewLease_requiresCurrentOwnerAndUnexpiredLease() throws Exception {
        withRepository(repository -> {
            repository.insertReadyBatch(List.of(message("m-1")), NOW);
            repository.claimReadyBatch(new ClaimRequest(
                    "lead", "c-1", "owner-1", 10, NOW, NOW.plusSeconds(60)));

            assertEquals(1, repository.renewLease(
                    "m-1", "owner-1", 1, NOW.plusSeconds(120), NOW.plusSeconds(10)));
            assertEquals(0, repository.renewLease(
                    "m-1", "owner-2", 1, NOW.plusSeconds(120), NOW.plusSeconds(10)));
            assertEquals(0, repository.renewLease(
                    "m-1", "owner-1", 1, NOW.plusSeconds(120), NOW.plusSeconds(121)));

            ConsumerRecordEntity saved = entity("m-1");
            assertEquals(NOW.plusSeconds(120).atOffset(java.time.ZoneOffset.UTC).toLocalDateTime(),
                    saved.getLeaseUntil());
        });
    }

    @Test
    void markConsumed_withWrongOwner_doesNotUpdate() throws Exception {
        withRepository(repository -> {
            repository.insertReadyBatch(List.of(message("m-1")), NOW);
            repository.claimReadyBatch(new ClaimRequest(
                    "lead", "c-1", "owner-1", 10, NOW, NOW.plusSeconds(60)));

            assertEquals(0, repository.markConsumed(
                    "m-1", "owner-2", 1, NOW.plusSeconds(1)));
            assertEquals(0, repository.markConsumed(
                    "m-1", "owner-1", 2, NOW.plusSeconds(1)));
            assertEquals("PROCESSING", entity("m-1").getStatus());

            assertEquals(1, repository.markConsumed(
                    "m-1", "owner-1", 1, NOW.plusSeconds(1)));
            assertEquals("CONSUMED", entity("m-1").getStatus());
        });
    }

    @Test
    void markRetry_belowMax_returnsReady() throws Exception {
        withRepository(repository -> {
            repository.insertReadyBatch(List.of(message("m-1")), NOW);
            repository.claimReadyBatch(new ClaimRequest(
                    "lead", "c-1", "owner-1", 10, NOW, NOW.plusSeconds(60)));

            assertEquals(1, repository.markRetry(
                    "m-1", "owner-1", 1, NOW.plusSeconds(30), 3, NOW.plusSeconds(1)));

            ConsumerRecordEntity saved = entity("m-1");
            assertEquals("READY", saved.getStatus());
            assertEquals(1, saved.getRetryCount());
            assertEquals(null, saved.getOwnerToken());
            assertEquals(null, saved.getLeaseUntil());
        });
    }

    @Test
    void markRetry_atMax_becomesDead() throws Exception {
        withRepository(repository -> {
            repository.insertReadyBatch(List.of(message("m-1")), NOW);
            repository.claimReadyBatch(new ClaimRequest(
                    "lead", "c-1", "owner-1", 10, NOW, NOW.plusSeconds(60)));

            assertEquals(1, repository.markRetry(
                    "m-1", "owner-1", 1, NOW.plusSeconds(30), 1, NOW.plusSeconds(1)));

            ConsumerRecordEntity saved = entity("m-1");
            assertEquals("DEAD", saved.getStatus());
            assertEquals(1, saved.getRetryCount());
            assertEquals(null, saved.getNextRetryAt());
        });
    }

    @Test
    void findExpiredLeases_returnsOnlyExpiredProcessing() throws Exception {
        withRepository(repository -> {
            repository.insertReadyBatch(List.of(message("m-1")), NOW);
            repository.claimReadyBatch(new ClaimRequest(
                    "lead", "c-1", "owner-1", 10, NOW, NOW.plusSeconds(60)));

            assertTrue(repository.findExpiredLeases(
                    NOW.plusSeconds(30), 10).isEmpty());

            List<ConsumerRecord> expired = repository.findExpiredLeases(
                    NOW.plusSeconds(61), 10);
            assertEquals(1, expired.size());
            assertEquals("m-1", expired.get(0).msgId());
        });
    }

    private ConsumedMessage message(String msgId) {
        return new ConsumedMessage(
                msgId, "lead", "lead-1", "LEAD_ASSIGNED", "1-0", "{}", null);
    }

    private ConsumerRecordEntity entity(String msgId) throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return session.getMapper(ConsumerRecordMapper.class).selectById(msgId);
        }
    }

    private void withRepository(RepositoryAssertion assertion) throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            ConsumerRecordMapper mapper = session.getMapper(ConsumerRecordMapper.class);
            assertion.run(new MybatisConsumerRecordRepository(mapper));
        }
    }

    @FunctionalInterface
    private interface RepositoryAssertion {
        void run(MybatisConsumerRecordRepository repository) throws Exception;
    }
}
