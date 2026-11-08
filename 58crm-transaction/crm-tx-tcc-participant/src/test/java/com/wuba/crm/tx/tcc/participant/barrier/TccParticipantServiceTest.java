package com.wuba.crm.tx.tcc.participant.barrier;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.wuba.crm.tx.tcc.api.BusinessActionContext;
import com.wuba.crm.tx.tcc.api.TccAction;
import com.wuba.crm.tx.tcc.api.TccException;
import com.wuba.crm.tx.tcc.api.TccPhaseResult;
import com.wuba.crm.tx.tcc.api.TccResultCode;
import com.wuba.crm.tx.tcc.api.TccTryResult;
import com.wuba.crm.tx.tcc.participant.action.TccActionRegistry;
import org.apache.ibatis.session.SqlSessionFactory;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 barrier 的幂等、空回滚、悬挂和 Try/Confirm/Cancel 事务语义。
 */
class TccParticipantServiceTest {

    private static final String ACTION = "assignLead";

    private AnnotationConfigApplicationContext context;
    private TccParticipantService service;
    private RecordingAction action;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        service = context.getBean(TccParticipantService.class);
        action = context.getBean(RecordingAction.class);
        jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("DROP TABLE IF EXISTS t_branch_barrier");
        jdbc.execute("""
                CREATE TABLE t_branch_barrier (
                    xid BIGINT NOT NULL,
                    branch_id BIGINT NOT NULL,
                    action_name VARCHAR(128) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (xid, branch_id)
                )
                """);
        context.getBean(TccActionRegistry.class).register(ACTION, action);
    }

    @Test
    void tryAction_insertsTriedBarrierAndRunsBusiness() {
        assertEquals(TccTryResult.SUCCESS, service.tryAction(context(1L, 1L)));

        assertEquals("TRIED", status(1L, 1L));
        assertEquals(1, action.tryCount.get());
    }

    @Test
    void duplicateTry_isIdempotent() {
        service.tryAction(context(1L, 1L));
        service.tryAction(context(1L, 1L));

        assertEquals(1, action.tryCount.get());
    }

    @Test
    void tryFailure_rollsBackBarrier() {
        action.tryResult = TccTryResult.FAILURE;

        assertEquals(TccTryResult.FAILURE, service.tryAction(context(1L, 1L)));

        assertEquals(0, count());
    }

    @Test
    void confirm_marksBarrierConfirmed() {
        service.tryAction(context(1L, 1L));

        assertEquals(TccPhaseResult.SUCCESS, service.confirm(context(1L, 1L)));
        assertEquals("CONFIRMED", status(1L, 1L));
        assertEquals(1, action.confirmCount.get());
    }

    @Test
    void duplicateConfirm_isIdempotent() {
        service.tryAction(context(1L, 1L));
        service.confirm(context(1L, 1L));
        service.confirm(context(1L, 1L));

        assertEquals(1, action.confirmCount.get());
    }

    @Test
    void confirmWithoutTry_isRejected() {
        TccException failure = assertThrows(TccException.class,
                () -> service.confirm(context(1L, 1L)));

        assertEquals(TccResultCode.TCC_INVALID_STATE, failure.getCode());
    }

    @Test
    void cancelWithoutTry_insertsSuspended() {
        assertEquals(TccPhaseResult.SUCCESS, service.cancel(context(1L, 1L)));
        assertEquals("SUSPENDED", status(1L, 1L));
    }

    @Test
    void cancelWithUnknownAction_doesNotPoisonBarrier() {
        BusinessActionContext unknown = new BusinessActionContext(1L, 1L, "missing", "{}");

        TccException failure = assertThrows(TccException.class, () -> service.cancel(unknown));

        assertEquals(TccResultCode.TCC_NOT_FOUND, failure.getCode());
        assertEquals(0, count());
    }

    @Test
    void cancelAfterTry_marksCancelledAndLateTryIsRejected() {
        service.tryAction(context(1L, 1L));

        assertEquals(TccPhaseResult.SUCCESS, service.cancel(context(1L, 1L)));
        assertEquals("CANCELLED", status(1L, 1L));
        assertEquals(TccTryResult.FAILURE, service.tryAction(context(1L, 1L)));
    }

    @Test
    void confirmAfterCancel_isRejected() {
        service.tryAction(context(1L, 1L));
        service.cancel(context(1L, 1L));

        TccException failure = assertThrows(TccException.class,
                () -> service.confirm(context(1L, 1L)));

        assertEquals(TccResultCode.TCC_INVALID_STATE, failure.getCode());
    }

    @Test
    void cancelAfterConfirm_isRejected() {
        service.tryAction(context(1L, 1L));
        service.confirm(context(1L, 1L));

        TccException failure = assertThrows(TccException.class,
                () -> service.cancel(context(1L, 1L)));

        assertEquals(TccResultCode.TCC_INVALID_STATE, failure.getCode());
    }

    @Test
    void concurrentTryAndCancel_endsInValidTerminalState() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread tryThread = new Thread(() -> {
            try {
                start.await();
                service.tryAction(context(1L, 1L));
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            } finally {
                done.countDown();
            }
        });
        Thread cancelThread = new Thread(() -> {
            try {
                start.await();
                service.cancel(context(1L, 1L));
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            } finally {
                done.countDown();
            }
        });
        tryThread.start();
        cancelThread.start();
        start.countDown();

        assertTrue(done.await(30, TimeUnit.SECONDS), "barrier race timed out");
        assertTrue(failure.get() == null, "barrier race failed: " + failure.get());
        String finalStatus = status(1L, 1L);
        assertTrue("CANCELLED".equals(finalStatus) || "SUSPENDED".equals(finalStatus),
                "unexpected final status: " + finalStatus);
    }

    private BusinessActionContext context(long xid, long branchId) {
        return new BusinessActionContext(xid, branchId, ACTION, "{}");
    }

    private String status(long xid, long branchId) {
        return jdbc.queryForObject(
                "SELECT status FROM t_branch_barrier WHERE xid = ? AND branch_id = ?",
                String.class, xid, branchId);
    }

    private int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM t_branch_barrier", Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * 可观测的测试动作。
     */
    public static class RecordingAction implements TccAction {

        private final AtomicInteger tryCount = new AtomicInteger();
        private final AtomicInteger confirmCount = new AtomicInteger();
        private final AtomicInteger cancelCount = new AtomicInteger();
        private TccTryResult tryResult = TccTryResult.SUCCESS;

        @Override
        public TccTryResult tryAction(BusinessActionContext context) {
            tryCount.incrementAndGet();
            return tryResult;
        }

        @Override
        public TccPhaseResult confirmAction(BusinessActionContext context) {
            confirmCount.incrementAndGet();
            return TccPhaseResult.SUCCESS;
        }

        @Override
        public TccPhaseResult cancelAction(BusinessActionContext context) {
            cancelCount.incrementAndGet();
            return TccPhaseResult.SUCCESS;
        }
    }

    /**
     * 测试装配。
     */
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:tcc-participant;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
            configuration.addMapper(BarrierMapper.class);
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
        BarrierMapper barrierMapper(SqlSessionTemplate sqlSessionTemplate) {
            return sqlSessionTemplate.getMapper(BarrierMapper.class);
        }

        @Bean
        BarrierRepository barrierRepository(BarrierMapper mapper) {
            return new MybatisBarrierRepository(mapper);
        }

        @Bean
        TccActionRegistry tccActionRegistry() {
            return new TccActionRegistry();
        }

        @Bean
        RecordingAction recordingAction() {
            return new RecordingAction();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        TccParticipantService tccParticipantService(
                BarrierRepository barriers, TccActionRegistry registry) {
            return new TccParticipantService(barriers, registry);
        }
    }
}
