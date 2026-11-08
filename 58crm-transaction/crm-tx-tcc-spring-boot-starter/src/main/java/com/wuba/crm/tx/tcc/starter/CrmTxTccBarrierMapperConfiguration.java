package com.wuba.crm.tx.tcc.starter;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;

/**
 * 有 MyBatis 工厂时扫描 BarrierMapper。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(SqlSessionFactory.class)
@AutoConfigureAfter(MybatisPlusAutoConfiguration.class)
@MapperScan(
        basePackages = "com.wuba.crm.tx.tcc.participant.barrier",
        annotationClass = Mapper.class
)
class CrmTxTccBarrierMapperConfiguration {
}
