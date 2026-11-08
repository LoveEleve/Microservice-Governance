package com.wuba.crm.tx.starter;

import com.wuba.crm.tx.tcc.participant.action.TccActionRegistry;
import com.wuba.crm.tx.tcc.participant.barrier.BarrierMapper;
import com.wuba.crm.tx.tcc.participant.barrier.BarrierRepository;
import com.wuba.crm.tx.tcc.participant.barrier.TccParticipantService;
import com.wuba.crm.tx.tcc.participant.grpc.TccParticipantCallbackGrpcService;
import com.wuba.crm.tx.tcc.participant.grpc.TccParticipantGrpcServer;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Participant Starter 自动装配和关闭开关。
 */
class CrmTxTccParticipantAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CrmTxTccParticipantAutoConfiguration.class))
            .withUserConfiguration(TestBeans.class)
            .withPropertyValues(
                    "crm.tx.tcc.participant.grpc-port=0");

    @Test
    void autoConfiguresParticipantInfrastructure() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(TccActionRegistry.class);
            assertThat(context).hasSingleBean(BarrierRepository.class);
            assertThat(context).hasSingleBean(TccParticipantService.class);
            assertThat(context).hasSingleBean(TccParticipantCallbackGrpcService.class);
            assertThat(context).hasSingleBean(TccParticipantGrpcServer.class);
        });
    }

    @Test
    void autoCreatesBarrierMapperFromSqlSessionTemplate() {
        ApplicationContextRunner sqlSessionRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        CrmTxTccParticipantAutoConfiguration.class))
                .withUserConfiguration(SqlSessionBeans.class)
                .withPropertyValues("crm.tx.tcc.participant.grpc-port=0");

        sqlSessionRunner.run(context -> {
            assertThat(context).hasSingleBean(BarrierMapper.class);
            assertThat(context).hasSingleBean(TccParticipantService.class);
        });
    }

    @Test
    void disabledPropertyStopsAutoConfiguration() {
        runner.withPropertyValues("crm.tx.tcc.participant.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(TccParticipantService.class);
            assertThat(context).doesNotHaveBean(TccParticipantGrpcServer.class);
        });
    }

    @Configuration
    static class TestBeans {

        @Bean
        BarrierMapper barrierMapper() {
            return Mockito.mock(BarrierMapper.class);
        }
    }

    @Configuration
    static class SqlSessionBeans {

        @Bean
        BarrierMapper barrierMapper() {
            return Mockito.mock(BarrierMapper.class);
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate() {
            SqlSessionTemplate template = Mockito.mock(SqlSessionTemplate.class);
            Mockito.when(template.getMapper(BarrierMapper.class))
                    .thenReturn(Mockito.mock(BarrierMapper.class));
            return template;
        }
    }
}
