package com.wuba.crm.tx.tcc.coordinator.config;

import com.wuba.crm.tx.tcc.id.IdSegmentMapper;
import com.wuba.crm.tx.tcc.id.MybatisSegmentAllocator;
import com.wuba.crm.tx.tcc.id.SegmentIdGenerator;
import com.wuba.crm.tx.tcc.id.TccXidGenerator;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.Clock;
import java.time.Duration;

/**
 * Coordinator 运行配置。
 *
 * <p>这里只装配 XID、时钟和数据库扫描；TCC 状态机由
 * {@code TccCoordinatorService} 负责。</p>
 */
@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
@MapperScan({
        "com.wuba.crm.tx.tcc.coordinator.persistence",
        "com.wuba.crm.tx.tcc.id"
})
public class TccCoordinatorConfiguration {

    /**
     * TCC 时间统一使用 UTC 时钟。
     *
     * @return UTC 时钟
     */
    @Bean
    public Clock tccClock() {
        return Clock.systemUTC();
    }

    /**
     * 创建基于 MyBatis-Plus 的号段分配器。
     *
     * @param mapper id_segment Mapper
     * @return 号段分配器
     */
    @Bean
    public MybatisSegmentAllocator mybatisSegmentAllocator(IdSegmentMapper mapper) {
        return new MybatisSegmentAllocator(mapper);
    }

    /**
     * 创建双 buffer 发号器。
     *
     * @param allocator 号段分配器
     * @return 发号器
     */
    @Bean(destroyMethod = "close")
    public SegmentIdGenerator tccSegmentIdGenerator(MybatisSegmentAllocator allocator) {
        return new SegmentIdGenerator(
                1000,
                1_000_000,
                Duration.ofMinutes(15).toMillis(),
                0.9,
                allocator);
    }

    /**
     * 创建 TCC XID 生成器。
     *
     * @param segmentIdGenerator 双 buffer 发号器
     * @return XID 生成器
     */
    @Bean
    public TccXidGenerator tccXidGenerator(SegmentIdGenerator segmentIdGenerator) {
        return new TccXidGenerator(segmentIdGenerator);
    }
}
