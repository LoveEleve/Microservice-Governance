package com.wuba.crm.tx.tcc.id;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 MyBatis-Plus 分配器的事务原子性和并发安全。
 */
class MybatisSegmentAllocatorTest {

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private MybatisSegmentAllocator allocator;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("DROP TABLE IF EXISTS id_segment");
        jdbc.execute("""
                CREATE TABLE id_segment (
                    biz_type VARCHAR(64) PRIMARY KEY,
                    max_id BIGINT NOT NULL,
                    step INT NOT NULL
                )
                """);
        jdbc.update("INSERT INTO id_segment (biz_type, max_id, step) VALUES (?, ?, ?)",
                TccXidGenerator.BIZ_TYPE, 0L, 100);
        allocator = context.getBean(MybatisSegmentAllocator.class);
        transactionTemplate = new TransactionTemplate(
                context.getBean(PlatformTransactionManager.class));
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void allocateMaxId_advancesWatermark() {
        assertEquals(100L, allocator.allocateMaxId(TccXidGenerator.BIZ_TYPE, 100));
        assertEquals(200L, allocator.allocateMaxId(TccXidGenerator.BIZ_TYPE, 100));
        assertEquals(200L, jdbc.queryForObject(
                "SELECT max_id FROM id_segment WHERE biz_type = ?",
                Long.class, TccXidGenerator.BIZ_TYPE));
    }

    @Test
    void configuredStep_readsDatabaseValue() {
        assertEquals(100, allocator.configuredStep(TccXidGenerator.BIZ_TYPE));
    }

    @Test
    void missingBizType_throws() {
        assertThrows(IllegalStateException.class,
                () -> allocator.allocateMaxId("missing", 1));
    }

    @Test
    void invalidStep_isRejectedBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> allocator.allocateMaxId(TccXidGenerator.BIZ_TYPE, 0));
    }

    @Test
    void concurrentAllocations_neverOverlap() throws Exception {
        int threads = 8;
        int perThread = 25;
        ConcurrentLinkedQueue<Long> maxIds = new ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        maxIds.add(allocator.allocateMaxId(TccXidGenerator.BIZ_TYPE, 100));
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "allocator-test-" + t);
            worker.start();
        }

        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "allocator workers timed out");

        Set<Long> unique = new HashSet<>(maxIds);
        assertEquals(threads * perThread, maxIds.size());
        assertEquals(threads * perThread, unique.size(), "overlapping segments detected");
        assertEquals((long) threads * perThread * 100, jdbc.queryForObject(
                "SELECT max_id FROM id_segment WHERE biz_type = ?",
                Long.class, TccXidGenerator.BIZ_TYPE));
    }

    @Test
    void requiresNew_commitsWhenOuterTransactionRollsBack() {
        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            allocator.allocateMaxId(TccXidGenerator.BIZ_TYPE, 100);
            throw new IllegalStateException("business failed");
        }));

        assertEquals(100L, jdbc.queryForObject(
                "SELECT max_id FROM id_segment WHERE biz_type = ?",
                Long.class, TccXidGenerator.BIZ_TYPE));
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:tcc-id;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
            configuration.addMapper(IdSegmentMapper.class);
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
        IdSegmentMapper idSegmentMapper(SqlSessionTemplate sqlSessionTemplate) {
            return sqlSessionTemplate.getMapper(IdSegmentMapper.class);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        MybatisSegmentAllocator mybatisSegmentAllocator(IdSegmentMapper mapper) {
            return new MybatisSegmentAllocator(mapper);
        }
    }
}
