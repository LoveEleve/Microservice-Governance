package com.wuba.crm.tx.tcc.example.coupon;

import com.wuba.crm.tx.outbox.api.OutboxPublisher;
import com.wuba.crm.tx.outbox.config.BizCodeRegistry;
import com.wuba.crm.tx.outbox.persistence.LocalMessageMapper;
import com.wuba.crm.tx.outbox.persistence.MybatisOutboxPublisher;
import com.wuba.crm.tx.tcc.example.coupon.persistence.CouponMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * coupon 业务 Bean 配置。
 */
@Configuration
public class CouponConfiguration {

    @Bean
    public BizCodeRegistry couponBizCodeRegistry() {
        return BizCodeRegistry.of("coupon");
    }

    @Bean
    public OutboxPublisher couponOutboxPublisher(
            LocalMessageMapper mapper, BizCodeRegistry registry) {
        return new MybatisOutboxPublisher(mapper, registry);
    }

    @Bean
    public CouponTccAction couponTccAction(
            CouponMapper mapper, OutboxPublisher outbox) {
        return new CouponTccAction(mapper, outbox);
    }
}
