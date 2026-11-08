package com.wuba.crm.tx.tcc.starter;

import com.wuba.crm.tx.tcc.participant.action.TccActionRegistrar;
import com.wuba.crm.tx.tcc.participant.action.TccActionRegistry;
import com.wuba.crm.tx.tcc.participant.barrier.BarrierMapper;
import com.wuba.crm.tx.tcc.participant.barrier.BarrierRepository;
import com.wuba.crm.tx.tcc.participant.barrier.MybatisBarrierRepository;
import com.wuba.crm.tx.tcc.participant.barrier.TccParticipantService;
import com.wuba.crm.tx.tcc.participant.grpc.TccParticipantCallbackGrpcService;
import com.wuba.crm.tx.tcc.participant.grpc.TccParticipantGrpcServer;
import com.wuba.crm.tx.tcc.participant.web.TccParticipantTryController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * TCC RM 参与方自动装配。
 */
@AutoConfiguration
@ConditionalOnClass(TccParticipantService.class)
@EnableConfigurationProperties(CrmTxTccProperties.class)
@ConditionalOnProperty(
        prefix = "crm.tx.tcc.participant",
        name = "enabled",
        havingValue = "true")
@EnableTransactionManagement(proxyTargetClass = true)
@Import(CrmTxTccBarrierMapperConfiguration.class)
public class CrmTxTccParticipantAutoConfiguration {

    /**
     * 动作注册表。
     *
     * @return registry
     */
    @Bean
    @ConditionalOnMissingBean
    public TccActionRegistry tccActionRegistry() {
        return new TccActionRegistry();
    }

    /**
     * 自动注册 @TccActionHandler。
     *
     * @param context  Spring context
     * @param registry registry
     * @return registrar
     */
    @Bean
    @ConditionalOnMissingBean
    public TccActionRegistrar tccActionRegistrar(
            ApplicationContext context, TccActionRegistry registry) {
        return new TccActionRegistrar(context, registry);
    }

    /**
     * barrier 仓储。
     *
     * @param mapper mapper
     * @return repository
     */
    @Bean
    @ConditionalOnMissingBean
    public BarrierRepository tccBarrierRepository(BarrierMapper mapper) {
        return new MybatisBarrierRepository(mapper);
    }

    /**
     * participant 执行器。
     *
     * @param barriers       repository
     * @param actionRegistry registry
     * @return service
     */
    @Bean
    @ConditionalOnMissingBean
    public TccParticipantService tccParticipantService(
            BarrierRepository barriers, TccActionRegistry actionRegistry) {
        return new TccParticipantService(barriers, actionRegistry);
    }

    /**
     * 通用 Try endpoint。
     *
     * @param service participant service
     * @return controller
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(
            prefix = "crm.tx.tcc.participant",
            name = "try-endpoint-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public TccParticipantTryController tccParticipantTryController(
            TccParticipantService service) {
        return new TccParticipantTryController(service);
    }

    /**
     * callback adapter。
     *
     * @param service participant service
     * @return adapter
     */
    @Bean
    @ConditionalOnMissingBean
    public TccParticipantCallbackGrpcService tccParticipantCallbackGrpcService(
            TccParticipantService service) {
        return new TccParticipantCallbackGrpcService(service);
    }

    /**
     * callback gRPC server。
     *
     * @param service callback adapter
     * @param properties 配置
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
            CrmTxTccProperties properties) {
        return new TccParticipantGrpcServer(
                service, properties.getParticipant().getGrpcPort());
    }
}
