package com.wuba.crm.tx.tcc.participant.barrier;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.wuba.crm.tx.outbox.api.BusinessEvent;
import com.wuba.crm.tx.outbox.api.OutboxPublisher;
import com.wuba.crm.tx.outbox.config.BizCodeRegistry;
import com.wuba.crm.tx.outbox.persistence.LocalMessageMapper;
import com.wuba.crm.tx.outbox.persistence.MybatisOutboxPublisher;
import com.wuba.crm.tx.tcc.api.BusinessActionContext;
import com.wuba.crm.tx.tcc.api.TccAction;
import com.wuba.crm.tx.tcc.api.TccPhaseResult;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证业务 TccAction 在 Confirm 事务内同时写业务终态和 Outbox。
 */
class TccOutboxTransactionTest {

    private static final String ACTION = "assignLead";

    private AnnotationConfigApplicationContext context;
    private TccParticipantService service;
    private LeadAssignAction action;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        service = context.getBean(TccParticipantService.class);
        action = context.getBean(LeadAssignAction.class);
        jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("DROP TABLE IF EXISTS t_branch_barrier");
        jdbc.execute("DROP TABLE IF EXISTS t_local_message");
        jdbc.execute("DROP TABLE IF EXISTS t_lead");
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
        jdbc.execute("""
                CREATE TABLE t_lead (
                    id BIGINT PRIMARY KEY,
                    status VARCHAR(16) NOT NULL
                )
                """);
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
                    retry_count INT NOT NULL,
                    next_retry_at TIMESTAMP,
                    stream_entry_id VARCHAR(64),
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.update("INSERT INTO t_lead (id, status) VALUES (1, 'NEW')");
        context.getBean(TccActionRegistry.class).register(ACTION, action);
    }

    @Test
    void confirm_writesBusinessAndOutboxInSameTransaction() {
        BusinessActionContext ctx = context(1L, 1L);

        assertEquals(TccTryResult.SUCCESS, service.tryAction(ctx));
        assertEquals(TccPhaseResult.SUCCESS, service.confirm(ctx));

        assertEquals("ASSIGNED", jdbc.queryForObject(
                "SELECT status FROM t_lead WHERE id = 1", String.class));
        assertEquals("CONFIRMED", jdbc.queryForObject(
                "SELECT status FROM t_branch_barrier", String.class));
        assertEquals(1, countMessages());
        assertEquals("INIT", jdbc.queryForObject(
                "SELECT status FROM t_local_message", String.class));
    }

    @Test
    void confirmRetryable_rollsBackBusinessAndOutbox() {
        BusinessActionContext ctx = context(1L, 1L);
        service.tryAction(ctx);
        action.confirmResult = TccPhaseResult.RETRYABLE;

        assertEquals(TccPhaseResult.RETRYABLE, service.confirm(ctx));

        assertEquals("ASSIGNING", jdbc.queryForObject(
                "SELECT status FROM t_lead WHERE id = 1", String.class));
        assertEquals("TRIED", jdbc.queryForObject(
                "SELECT status FROM t_branch_barrier", String.class));
        assertEquals(0, countMessages());
    }

    private BusinessActionContext context(long xid, long branchId) {
        return new BusinessActionContext(xid, branchId, ACTION, "{}");
    }

    private int countMessages() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM t_local_message", Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * 业务示例：Confirm 内写业务终态和终态事件。
     */
    public static class LeadAssignAction implements TccAction {

        private final JdbcTemplate jdbc;
        private final OutboxPublisher outbox;
        private TccPhaseResult confirmResult = TccPhaseResult.SUCCESS;

        public LeadAssignAction(JdbcTemplate jdbc, OutboxPublisher outbox) {
            this.jdbc = jdbc;
            this.outbox = outbox;
        }

        @Override
        public TccTryResult tryAction(BusinessActionContext context) {
            jdbc.update("UPDATE t_lead SET status = 'ASSIGNING' WHERE id = 1");
            return TccTryResult.SUCCESS;
        }

        @Override
        public TccPhaseResult confirmAction(BusinessActionContext context) {
            jdbc.update("UPDATE t_lead SET status = 'ASSIGNED' WHERE id = 1");
            outbox.publish(new BusinessEvent(
                    "lead-assigned:" + context.xid() + ":" + context.branchId(),
                    "lead",
                    "L-1",
                    "LEAD_ASSIGNED",
                    "{}"));
            return confirmResult;
        }

        @Override
        public TccPhaseResult cancelAction(BusinessActionContext context) {
            jdbc.update("UPDATE t_lead SET status = 'NEW' WHERE id = 1");
            return TccPhaseResult.SUCCESS;
        }
    }

    /**
     * 测试装配：barrier 和 outbox 共用同一个 H2 本地事务。
     */
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:tcc-outbox;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
        BarrierMapper barrierMapper(SqlSessionTemplate sqlSessionTemplate) {
            return sqlSessionTemplate.getMapper(BarrierMapper.class);
        }

        @Bean
        LocalMessageMapper localMessageMapper(SqlSessionTemplate sqlSessionTemplate) {
            return sqlSessionTemplate.getMapper(LocalMessageMapper.class);
        }

        @Bean
        BarrierRepository barrierRepository(BarrierMapper mapper) {
            return new MybatisBarrierRepository(mapper);
        }

        @Bean
        BizCodeRegistry bizCodeRegistry() {
            return BizCodeRegistry.of("lead");
        }

        @Bean
        OutboxPublisher outboxPublisher(
                LocalMessageMapper mapper, BizCodeRegistry registry) {
            return new MybatisOutboxPublisher(mapper, registry);
        }

        @Bean
        TccActionRegistry tccActionRegistry() {
            return new TccActionRegistry();
        }

        @Bean
        LeadAssignAction leadAssignAction(JdbcTemplate jdbc, OutboxPublisher outbox) {
            return new LeadAssignAction(jdbc, outbox);
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
