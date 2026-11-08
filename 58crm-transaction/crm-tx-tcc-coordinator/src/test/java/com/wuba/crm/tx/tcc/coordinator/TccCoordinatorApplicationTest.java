package com.wuba.crm.tx.tcc.coordinator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证 Spring Boot 装配能创建 Coordinator 的全部 Bean，并启动/关闭 gRPC server。
 */
@SpringBootTest(
        classes = TccCoordinatorApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:tcc-coordinator-context;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "crm.tx.tcc.coordinator.grpc-port=0"
        })
class TccCoordinatorApplicationTest {

    @Test
    void contextLoads() {
    }
}
