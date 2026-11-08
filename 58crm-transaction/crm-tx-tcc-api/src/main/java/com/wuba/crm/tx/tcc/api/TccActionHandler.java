package com.wuba.crm.tx.tcc.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个参与方 TCC 动作。
 *
 * <p>业务 Bean 实现 {@link TccAction}，标注本注解后由 Participant Starter
 * 自动注册到 {@code TccActionRegistry}。</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TccActionHandler {

    /**
     * 动作名，必须和 TM 注册分支时一致。
     *
     * @return 动作名
     */
    String value();
}
