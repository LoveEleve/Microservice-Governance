package com.wuba.crm.tx.e2e.quota;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * E2E 参与方（RM/消费方）服务。
 */
@SpringBootApplication
@MapperScan(
        basePackages = {
                "com.wuba.crm.tx.e2e.quota.persistence",
                "com.wuba.crm.tx.tcc.participant.barrier",
                "com.wuba.crm.tx.outbox.persistence"
        },
        annotationClass = Mapper.class)
public class QuotaApplication {

    /**
     * 启动 quota 服务。
     *
     * @param args 参数
     */
    public static void main(String[] args) {
        SpringApplication.run(QuotaApplication.class, args);
    }
}
