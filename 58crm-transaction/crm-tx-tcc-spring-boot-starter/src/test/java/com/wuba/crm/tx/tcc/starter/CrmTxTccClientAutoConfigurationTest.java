package com.wuba.crm.tx.tcc.starter;

import com.wuba.crm.tx.tcc.api.TccTransactionTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 TCC client 自动装配和直连地址。
 */
class CrmTxTccClientAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CrmTxTccClientAutoConfiguration.class))
            .withPropertyValues(
                    "crm.tx.tcc.client.coordinator-address=127.0.0.1:9090");

    @Test
    void configuresTemplate() {
        runner.run(context -> assertThat(context).hasSingleBean(TccTransactionTemplate.class));
    }

    @Test
    void disabledPropertyStopsAutoConfiguration() {
        runner.withPropertyValues("crm.tx.tcc.client.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(TccTransactionTemplate.class));
    }
}
