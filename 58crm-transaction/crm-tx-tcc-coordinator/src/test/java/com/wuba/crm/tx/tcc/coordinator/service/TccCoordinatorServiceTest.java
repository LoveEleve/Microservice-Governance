package com.wuba.crm.tx.tcc.coordinator.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.wuba.crm.tx.tcc.api.BranchStatus;
import com.wuba.crm.tx.tcc.api.GlobalStatus;
import com.wuba.crm.tx.tcc.api.TccBeginRequest;
import com.wuba.crm.tx.tcc.api.TccBeginResult;
import com.wuba.crm.tx.tcc.api.TccBranchRegisterRequest;
import com.wuba.crm.tx.tcc.api.TccBranchRegisterResult;
import com.wuba.crm.tx.tcc.api.TccBranchReportRequest;
import com.wuba.crm.tx.tcc.api.TccException;
import com.wuba.crm.tx.tcc.api.TccResultCode;
import com.wuba.crm.tx.tcc.coordinator.persistence.MybatisTccBranchRepository;
import com.wuba.crm.tx.tcc.coordinator.persistence.MybatisTccGlobalRepository;
import com.wuba.crm.tx.tcc.coordinator.persistence.TccBranchMapper;
import com.wuba.crm.tx.tcc.coordinator.persistence.TccBranchRepository;
import com.wuba.crm.tx.tcc.coordinator.persistence.TccGlobalMapper;
import com.wuba.crm.tx.tcc.coordinator.persistence.TccGlobalRepository;
import com.wuba.crm.tx.tcc.id.InMemorySegmentAllocator;
import com.wuba.crm.tx.tcc.id.SegmentIdGenerator;
import com.wuba.crm.tx.tcc.id.TccXidGenerator;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 Begin、RegisterBranch、ReportBranch 的持久化和幂等语义。
 */
class TccCoordinatorServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private AnnotationConfigApplicationContext context;
    private TccCoordinatorService service;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        service = context.getBean(TccCoordinatorService.class);
        jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("DROP TABLE IF EXISTS t_tcc_branch");
        jdbc.execute("DROP TABLE IF EXISTS t_tcc_global");
        jdbc.execute("""
                CREATE TABLE t_tcc_global (
                    xid BIGINT PRIMARY KEY,
                    request_id VARCHAR(128) NOT NULL UNIQUE,
                    biz_code VARCHAR(64) NOT NULL,
                    biz_id VARCHAR(128) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    timeout_at TIMESTAMP NOT NULL,
                    branch_seq BIGINT NOT NULL,
                    owner_token VARCHAR(64),
                    lease_until TIMESTAMP,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE t_tcc_branch (
                    xid BIGINT NOT NULL,
                    branch_id BIGINT NOT NULL,
                    resource_key VARCHAR(128) NOT NULL,
                    action_name VARCHAR(128) NOT NULL,
                    callback_endpoint VARCHAR(256) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    retry_count INT NOT NULL,
                    next_retry_at TIMESTAMP,
                    application_data VARCHAR(65535),
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (xid, branch_id),
                    UNIQUE (xid, resource_key)
                )
                """);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void begin_createsGlobalWithPositiveXid() {
        TccBeginResult result = service.begin(begin("req-1", "lead-1"));

        assertEquals(1L, result.xid());
        assertEquals(GlobalStatus.TRYING, result.status());
        assertEquals(1, count("t_tcc_global"));
    }

    @Test
    void begin_sameRequestId_isIdempotent() {
        TccBeginResult first = service.begin(begin("req-1", "lead-1"));
        TccBeginResult second = service.begin(begin("req-1", "lead-1"));

        assertEquals(first.xid(), second.xid());
        assertEquals(1, count("t_tcc_global"));
    }

    @Test
    void begin_sameRequestIdWithDifferentBiz_isRejected() {
        service.begin(begin("req-1", "lead-1"));

        TccException failure = assertThrows(TccException.class,
                () -> service.begin(begin("req-1", "lead-2")));

        assertEquals(TccResultCode.TCC_INVALID_STATE, failure.getCode());
    }

    @Test
    void concurrentBegin_sameRequestId_returnsOneXid() throws Exception {
        int threads = 8;
        ConcurrentLinkedQueue<Long> xids = new ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    xids.add(service.begin(begin("req-concurrent", "lead-1")).xid());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        assertEquals(true, done.await(30, TimeUnit.SECONDS));
        Set<Long> unique = new HashSet<>(xids);
        assertEquals(threads, xids.size());
        assertEquals(1, unique.size());
        assertEquals(1, count("t_tcc_global"));
    }

    @Test
    void concurrentRegister_allocatesUniqueBranchIds() throws Exception {
        long xid = service.begin(begin("req-concurrent", "lead-1")).xid();
        int threads = 8;
        ConcurrentLinkedQueue<Long> branchIds = new ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            int index = i;
            new Thread(() -> {
                try {
                    start.await();
                    branchIds.add(service.registerBranch(
                            branch(xid, "lead:" + index)).branchId());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        assertEquals(true, done.await(30, TimeUnit.SECONDS));
        assertEquals(threads, branchIds.size());
        assertEquals(threads, new HashSet<>(branchIds).size());
        assertEquals(8, count("t_tcc_branch"));
    }

    @Test
    void registerBranch_afterGlobalLeavesTry_isRejected() {
        long xid = service.begin(begin("req-1", "lead-1")).xid();
        jdbc.update("UPDATE t_tcc_global SET status = 'CANCELLING' WHERE xid = ?", xid);

        TccException failure = assertThrows(TccException.class,
                () -> service.registerBranch(branch(xid, "lead:1")));

        assertEquals(TccResultCode.TCC_INVALID_STATE, failure.getCode());
        assertEquals(0, count("t_tcc_branch"));
    }

    @Test
    void registerBranch_existingBranchRemainsIdempotentAfterTryTimeout() {
        long xid = service.begin(begin("req-1", "lead-1")).xid();
        long branchId = service.registerBranch(branch(xid, "lead:1")).branchId();
        jdbc.update("UPDATE t_tcc_global SET timeout_at = ? WHERE xid = ?",
                java.time.LocalDateTime.ofInstant(
                        java.time.Instant.parse("2025-12-31T00:00:00Z"),
                        java.time.ZoneOffset.UTC),
                xid);

        TccBranchRegisterResult retried = service.registerBranch(branch(xid, "lead:1"));

        assertEquals(branchId, retried.branchId());
    }

    @Test
    void registerBranch_afterTryTimeout_isRejected() {
        long xid = service.begin(begin("req-1", "lead-1")).xid();
        jdbc.update("UPDATE t_tcc_global SET timeout_at = ? WHERE xid = ?",
                java.time.LocalDateTime.ofInstant(
                        java.time.Instant.parse("2025-12-31T00:00:00Z"),
                        java.time.ZoneOffset.UTC),
                xid);

        TccException failure = assertThrows(TccException.class,
                () -> service.registerBranch(branch(xid, "lead:1")));

        assertEquals(TccResultCode.TCC_INVALID_STATE, failure.getCode());
    }

    @Test
    void registerBranch_allocatesSequentialIds() {
        TccBeginResult begin = service.begin(begin("req-1", "lead-1"));

        TccBranchRegisterResult first = service.registerBranch(branch(begin.xid(), "lead:1"));
        TccBranchRegisterResult second = service.registerBranch(branch(begin.xid(), "quota:1"));

        assertEquals(1L, first.branchId());
        assertEquals(2L, second.branchId());
        assertEquals(2L, jdbc.queryForObject(
                "SELECT branch_seq FROM t_tcc_global WHERE xid = ?", Long.class, begin.xid()));
    }

    @Test
    void registerBranch_sameResourceKey_isIdempotent() {
        long xid = service.begin(begin("req-1", "lead-1")).xid();

        TccBranchRegisterResult first = service.registerBranch(branch(xid, "lead:1"));
        TccBranchRegisterResult second = service.registerBranch(branch(xid, "lead:1"));

        assertEquals(first.branchId(), second.branchId());
        assertEquals(1, count("t_tcc_branch"));
    }

    @Test
    void registerBranch_sameResourceKeyDifferentAction_isRejected() {
        long xid = service.begin(begin("req-1", "lead-1")).xid();
        service.registerBranch(branch(xid, "lead:1"));

        TccBranchRegisterRequest conflicting = new TccBranchRegisterRequest(
                xid, "lead:1", "anotherAction",
                "http://localhost:9090/tcc", "{}");

        TccException failure = assertThrows(TccException.class,
                () -> service.registerBranch(conflicting));

        assertEquals(TccResultCode.TCC_INVALID_STATE, failure.getCode());
    }

    @Test
    void reportBranch_updatesTryResultAndIsIdempotent() {
        long xid = service.begin(begin("req-1", "lead-1")).xid();
        long branchId = service.registerBranch(branch(xid, "lead:1")).branchId();

        service.reportBranch(new TccBranchReportRequest(
                xid, branchId, BranchStatus.TRY_SUCCESS, null));
        service.reportBranch(new TccBranchReportRequest(
                xid, branchId, BranchStatus.TRY_SUCCESS, null));

        assertEquals("TRY_SUCCESS", jdbc.queryForObject(
                "SELECT status FROM t_tcc_branch WHERE xid = ?", String.class, xid));
    }

    @Test
    void reportBranch_conflictingResult_isRejected() {
        long xid = service.begin(begin("req-1", "lead-1")).xid();
        long branchId = service.registerBranch(branch(xid, "lead:1")).branchId();
        service.reportBranch(new TccBranchReportRequest(
                xid, branchId, BranchStatus.TRY_SUCCESS, null));

        TccException failure = assertThrows(TccException.class,
                () -> service.reportBranch(new TccBranchReportRequest(
                        xid, branchId, BranchStatus.TRY_FAIL, null)));

        assertEquals(TccResultCode.TCC_INVALID_STATE, failure.getCode());
    }

    @Test
    void reportBranch_afterGlobalLeavesTry_isIdempotentOrRejected() {
        long xid = service.begin(begin("req-1", "lead-1")).xid();
        long branchId = service.registerBranch(branch(xid, "lead:1")).branchId();
        service.reportBranch(new TccBranchReportRequest(
                xid, branchId, BranchStatus.TRY_SUCCESS, null));
        jdbc.update("UPDATE t_tcc_global SET status = 'CANCELLING' WHERE xid = ?", xid);

        service.reportBranch(new TccBranchReportRequest(
                xid, branchId, BranchStatus.TRY_SUCCESS, null));

        TccException failure = assertThrows(TccException.class,
                () -> service.reportBranch(new TccBranchReportRequest(
                        xid, branchId, BranchStatus.TRY_FAIL, null)));
        assertEquals(TccResultCode.TCC_INVALID_STATE, failure.getCode());
    }

    @Test
    void reportBranch_afterGlobalLeavesTry_rejectsUnreportedBranch() {
        long xid = service.begin(begin("req-1", "lead-1")).xid();
        long branchId = service.registerBranch(branch(xid, "lead:1")).branchId();
        jdbc.update("UPDATE t_tcc_global SET status = 'CANCELLING' WHERE xid = ?", xid);

        TccException failure = assertThrows(TccException.class,
                () -> service.reportBranch(new TccBranchReportRequest(
                        xid, branchId, BranchStatus.TRY_SUCCESS, null)));

        assertEquals(TccResultCode.TCC_INVALID_STATE, failure.getCode());
    }

    @Test
    void reportBranch_unknownBranch_isNotFound() {
        long xid = service.begin(begin("req-1", "lead-1")).xid();

        TccException failure = assertThrows(TccException.class,
                () -> service.reportBranch(new TccBranchReportRequest(
                        xid, 99L, BranchStatus.TRY_SUCCESS, null)));

        assertEquals(TccResultCode.TCC_NOT_FOUND, failure.getCode());
    }

    private TccBeginRequest begin(String requestId, String bizId) {
        return new TccBeginRequest(requestId, "lead", bizId, 30);
    }

    private TccBranchRegisterRequest branch(long xid, String resourceKey) {
        return new TccBranchRegisterRequest(
                xid, resourceKey, "assignLead",
                "http://localhost:9090/tcc", "{}");
    }

    private int count(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:tcc-coordinator;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
            configuration.addMapper(TccGlobalMapper.class);
            configuration.addMapper(TccBranchMapper.class);
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
        TccGlobalMapper tccGlobalMapper(SqlSessionTemplate sqlSessionTemplate) {
            return sqlSessionTemplate.getMapper(TccGlobalMapper.class);
        }

        @Bean
        TccBranchMapper tccBranchMapper(SqlSessionTemplate sqlSessionTemplate) {
            return sqlSessionTemplate.getMapper(TccBranchMapper.class);
        }

        @Bean
        TccGlobalRepository tccGlobalRepository(TccGlobalMapper mapper) {
            return new MybatisTccGlobalRepository(mapper);
        }

        @Bean
        TccBranchRepository tccBranchRepository(TccBranchMapper mapper) {
            return new MybatisTccBranchRepository(mapper);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        TccXidGenerator tccXidGenerator() {
            SegmentIdGenerator generator = new SegmentIdGenerator(
                    10, 10_000, 60_000, 0.9, new InMemorySegmentAllocator());
            return new TccXidGenerator(generator);
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        TccCoordinatorService tccCoordinatorService(
                TccGlobalRepository globals,
                TccBranchRepository branches,
                TccXidGenerator xidGenerator,
                Clock clock) {
            return new TccCoordinatorService(globals, branches, xidGenerator, clock);
        }
    }
}
