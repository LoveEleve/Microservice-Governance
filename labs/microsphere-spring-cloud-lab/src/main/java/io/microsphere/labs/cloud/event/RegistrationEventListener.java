package io.microsphere.labs.cloud.event;

import io.microsphere.spring.cloud.client.service.registry.event.RegistrationDeregisteredEvent;
import io.microsphere.spring.cloud.client.service.registry.event.RegistrationRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 服务注册生命周期事件展示。
 *
 * <p>Microsphere 通过 {@code EventPublishingRegistrationAspect} 发布四个事件：</p>
 * <ul>
 *     <li>{@code RegistrationPreRegisteredEvent}</li>
 *     <li>{@code RegistrationRegisteredEvent}</li>
 *     <li>{@code RegistrationPreDeregisteredEvent}</li>
 *     <li>{@code RegistrationDeregisteredEvent}</li>
 * </ul>
 */
@Component
public class RegistrationEventListener {

    private static final Logger log = LoggerFactory.getLogger(RegistrationEventListener.class);

    @EventListener
    public void onRegistered(RegistrationRegisteredEvent event) {
        log.info("[registration] registered: serviceId={}, instanceId={}, registry={}",
                event.getRegistration().getServiceId(),
                event.getRegistration().getInstanceId(),
                event.getRegistry().getClass().getSimpleName());
    }

    @EventListener
    public void onDeregistered(RegistrationDeregisteredEvent event) {
        log.info("[registration] deregistered: serviceId={}, instanceId={}, registry={}",
                event.getRegistration().getServiceId(),
                event.getRegistration().getInstanceId(),
                event.getRegistry().getClass().getSimpleName());
    }
}
