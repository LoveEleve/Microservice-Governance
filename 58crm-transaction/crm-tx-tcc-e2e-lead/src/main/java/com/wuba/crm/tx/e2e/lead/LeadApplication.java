package com.wuba.crm.tx.e2e.lead;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * E2E 业务方（TM）服务。
 */
@SpringBootApplication
@EnableFeignClients
public class LeadApplication {

    /**
     * 启动业务方服务。
     *
     * @param args 参数
     */
    public static void main(String[] args) {
        SpringApplication.run(LeadApplication.class, args);
    }
}
