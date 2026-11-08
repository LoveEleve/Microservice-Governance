package com.wuba.crm.tx.tcc.participant.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Participant 运行配置。
 *
 * <p>扫描 barrier Mapper，并启用本地事务代理。业务方还需要注册自己的
 * {@code TccAction} 到 {@code TccActionRegistry}。</p>
 */
@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
@MapperScan(
        basePackages = "com.wuba.crm.tx.tcc.participant.barrier",
        annotationClass = Mapper.class
)
public class TccParticipantConfiguration {
}
