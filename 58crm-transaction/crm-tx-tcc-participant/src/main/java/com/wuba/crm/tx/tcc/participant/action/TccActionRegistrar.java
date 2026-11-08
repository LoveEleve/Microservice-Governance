package com.wuba.crm.tx.tcc.participant.action;

import com.wuba.crm.tx.tcc.api.TccActionHandler;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.Map;

/**
 * 扫描并注册标注 {@link TccActionHandler} 的业务 Bean。
 */
public class TccActionRegistrar implements ApplicationListener<ApplicationReadyEvent> {

    private final ApplicationContext applicationContext;
    private final TccActionRegistry registry;

    /**
     * 创建注册器。
     *
     * @param applicationContext Spring context
     * @param registry           动作注册表
     */
    public TccActionRegistrar(
            ApplicationContext applicationContext,
            TccActionRegistry registry) {
        this.applicationContext = applicationContext;
        this.registry = registry;
    }

    /**
     * 应用就绪后注册所有动作。
     *
     * @param event ready event
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        registerAll();
    }

    /**
     * 执行扫描注册。
     */
    public void registerAll() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(TccActionHandler.class);
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            TccActionHandler annotation = AnnotationUtils.findAnnotation(
                    targetClass, TccActionHandler.class);
            if (annotation == null) {
                annotation = AnnotationUtils.findAnnotation(
                        bean.getClass(), TccActionHandler.class);
            }
            if (annotation == null) {
                continue;
            }
            if (!(bean instanceof com.wuba.crm.tx.tcc.api.TccAction)) {
                throw new IllegalStateException(
                        "TccActionHandler bean is not TccAction: " + entry.getKey());
            }
            registry.register(annotation.value(),
                    (com.wuba.crm.tx.tcc.api.TccAction) bean);
        }
    }
}
