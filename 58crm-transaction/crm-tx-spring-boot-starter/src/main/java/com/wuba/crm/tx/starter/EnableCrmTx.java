package com.wuba.crm.tx.starter;

import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 显式启用 CRM Transaction Starter。
 *
 * <p>Boot 3 也可能通过 AutoConfiguration.imports 自动装配；
 * 这个注解用于需要显式声明的团队。</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(CrmTxAutoConfiguration.class)
public @interface EnableCrmTx {
}
