package com.wuba.crm.tx.tcc.example.coupon;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 第二参与方（RM）：优惠券。
 */
@SpringBootApplication
@MapperScan(
        basePackages = {
                "com.wuba.crm.tx.tcc.example.coupon.persistence",
                "com.wuba.crm.tx.tcc.participant.barrier",
                "com.wuba.crm.tx.outbox.persistence"
        },
        annotationClass = Mapper.class)
public class CouponApplication {

    /**
     * 启动 coupon 服务。
     *
     * @param args 参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CouponApplication.class, args);
    }
}
