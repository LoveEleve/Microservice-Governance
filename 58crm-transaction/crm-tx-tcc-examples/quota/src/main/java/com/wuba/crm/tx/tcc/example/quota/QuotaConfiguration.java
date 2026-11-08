package com.wuba.crm.tx.tcc.example.quota;

import com.wuba.crm.tx.tcc.example.quota.persistence.QuotaMapper;
import com.wuba.crm.tx.outbox.api.OutboxPublisher;
import com.wuba.crm.tx.outbox.config.BizCodeRegistry;
import com.wuba.crm.tx.outbox.persistence.LocalMessageMapper;
import com.wuba.crm.tx.outbox.persistence.MybatisOutboxPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * quota 业务 Bean 配置。
 */
@Configuration
public class QuotaConfiguration {

    /**
     * quota TCC 动作。
     *
     * @param mapper Mapper
     * @param outbox Outbox 发布器
     * @return 动作
     */
    @Bean
    public BizCodeRegistry quotaBizCodeRegistry() {
        return BizCodeRegistry.of("quota");
    }

    @Bean
    public OutboxPublisher quotaOutboxPublisher(
            LocalMessageMapper mapper, BizCodeRegistry registry) {
        return new MybatisOutboxPublisher(mapper, registry);
    }

    @Bean
    public QuotaTccAction quotaTccAction(QuotaMapper mapper, OutboxPublisher outbox) {
        return new QuotaTccAction(mapper, outbox);
    }
}
