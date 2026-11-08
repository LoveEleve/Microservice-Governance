package com.wuba.crm.tx.tcc.starter;

import com.wuba.crm.tx.tcc.starter.aspect.TccBranchAspect;
import com.wuba.crm.tx.tcc.starter.aspect.TccExpressionResolver;
import com.wuba.crm.tx.tcc.starter.aspect.TccTransactionalAspect;
import com.wuba.crm.tx.tcc.client.TccTransactionManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * TCC 注解 AOP 自动装配。
 */
@AutoConfiguration
@AutoConfigureAfter(CrmTxTccClientAutoConfiguration.class)
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
@ConditionalOnBean(TccTransactionManager.class)
@ConditionalOnProperty(
        prefix = "crm.tx.tcc.annotation",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class TccAnnotationAutoConfiguration {

    /**
     * 表达式解析器。
     *
     * @return resolver
     */
    @Bean
    @ConditionalOnMissingBean
    public TccExpressionResolver tccExpressionResolver() {
        return new TccExpressionResolver();
    }

    /**
     * 全局事务 aspect。
     *
     * @param manager transaction manager
     * @param resolver resolver
     * @return aspect
     */
    @Bean
    @ConditionalOnMissingBean
    public TccTransactionalAspect tccTransactionalAspect(
            TccTransactionManager manager, TccExpressionResolver resolver) {
        return new TccTransactionalAspect(manager, resolver);
    }

    /**
     * 分支 aspect。
     *
     * @param manager transaction manager
     * @param resolver resolver
     * @return aspect
     */
    @Bean
    @ConditionalOnMissingBean
    public TccBranchAspect tccBranchAspect(
            TccTransactionManager manager, TccExpressionResolver resolver) {
        return new TccBranchAspect(manager, resolver);
    }
}
