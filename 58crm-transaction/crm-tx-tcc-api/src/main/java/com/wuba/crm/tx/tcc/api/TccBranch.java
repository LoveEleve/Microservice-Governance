package com.wuba.crm.tx.tcc.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个本地分支方法。
 *
 * <p>方法必须在独立 Spring Bean 上调用，避免自调用绕过 AOP。
 * 方法返回值必须是 {@link TccTryResult}，内部通常通过 Feign 调下游 Try。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TccBranch {

    /**
     * TCC 动作名。
     *
     * @return 动作名
     */
    String actionName();

    /**
     * resourceKey SpEL 表达式。
     *
     * @return 表达式
     */
    String resourceKey();

    /**
     * RM callback gRPC 地址，支持 ${} 占位符。
     *
     * @return endpoint
     */
    String callbackEndpoint();

    /**
     * applicationData SpEL 表达式。
     *
     * @return 表达式
     */
    String applicationData() default "";
}
