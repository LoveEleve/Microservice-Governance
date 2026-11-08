package com.wuba.crm.tx.tcc.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个业务方法为 TCC 全局事务入口。
 *
 * <p>属性支持 #{...} 表达式，例如 requestId = "#{#command.requestId}"。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TccTransactional {

    /**
     * 业务幂等键表达式。
     *
     * @return 表达式
     */
    String requestId();

    /**
     * 业务域。
     *
     * @return 业务域
     */
    String bizCode();

    /**
     * 业务单号表达式。
     *
     * @return 表达式
     */
    String bizId();

    /**
     * Try 超时秒数。
     *
     * @return 秒数
     */
    int tryTimeoutSeconds() default 30;
}
