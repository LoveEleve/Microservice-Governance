package io.microsphere.labs.seata.saga.config;

import io.seata.saga.engine.StateMachineEngine;
import io.seata.saga.engine.impl.DefaultStateMachineConfig;
import io.seata.saga.engine.impl.ProcessCtrlStateMachineEngine;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SagaConfig {

    @Bean
    public StateMachineEngine stateMachineEngine(ApplicationContext applicationContext) throws Exception {
        DefaultStateMachineConfig config = new DefaultStateMachineConfig();
        config.setApplicationContext(applicationContext);
        config.setResources(new String[]{"classpath:statelang/*.json"});
        config.afterPropertiesSet();

        ProcessCtrlStateMachineEngine engine = new ProcessCtrlStateMachineEngine();
        engine.setStateMachineConfig(config);
        return engine;
    }
}
