package com.wuba.crm.tx.starter;

import com.wuba.crm.tx.tcc.participant.action.TccActionRegistry;
import com.wuba.crm.tx.tcc.participant.barrier.BarrierMapper;
import com.wuba.crm.tx.tcc.participant.barrier.BarrierRepository;
import com.wuba.crm.tx.tcc.participant.barrier.MybatisBarrierRepository;
import com.wuba.crm.tx.tcc.participant.barrier.TccParticipantService;
import com.wuba.crm.tx.tcc.participant.grpc.TccParticipantCallbackGrpcService;
import com.wuba.crm.tx.tcc.participant.grpc.TccParticipantGrpcServer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * TCC Participant Starter 自动装配。
 *
 * <p>业务方只需要提供 {@link BarrierMapper} 和数据源；barrier、动作注册、
 * callback gRPC server 由 Starter 装配。</p>
 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "crm.tx.tcc.participant",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableTransactionManagement(proxyTargetClass = true)
@Import(CrmTxTccParticipantBarrierMapperConfiguration.class)
public class CrmTxTccParticipantAutoConfiguration {

    /**
     * 动作注册表。
     *
     * @return 注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public TccActionRegistry tccActionRegistry() {
        return new TccActionRegistry();
    }

    /**
     * barrier 仓储。
     *
     * @param mapper barrier Mapper
     * @return 仓储
     */
    @Bean
    @ConditionalOnMissingBean
    public BarrierRepository tccBarrierRepository(BarrierMapper mapper) {
        return new MybatisBarrierRepository(mapper);
    }

    /**
     * Participant 执行器。
     *
     * @param barriers       barrier 仓储
     * @param actionRegistry 动作注册表
     * @return 执行器
     */
    @Bean
    @ConditionalOnMissingBean
    public TccParticipantService tccParticipantService(
            BarrierRepository barriers, TccActionRegistry actionRegistry) {
        return new TccParticipantService(barriers, actionRegistry);
    }

    /**
     * participant callback adapter。
     *
     * @param participantService 执行器
     * @return adapter
     */
    @Bean
    @ConditionalOnMissingBean
    public TccParticipantCallbackGrpcService tccParticipantCallbackGrpcService(
            TccParticipantService participantService) {
        return new TccParticipantCallbackGrpcService(participantService);
    }

    /**
     * participant gRPC server。
     *
     * @param service callback adapter
     * @param port    gRPC 端口
     * @return server
     */
    @Bean(destroyMethod = "destroy")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "crm.tx.tcc.participant",
            name = "grpc-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public TccParticipantGrpcServer tccParticipantGrpcServer(
            TccParticipantCallbackGrpcService service,
            @Value("${crm.tx.tcc.participant.grpc-port:9091}") int port) {
        return new TccParticipantGrpcServer(service, port);
    }
}
