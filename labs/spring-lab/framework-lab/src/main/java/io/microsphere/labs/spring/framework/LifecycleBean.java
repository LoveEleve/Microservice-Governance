package io.microsphere.labs.spring.framework;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.ArrayList;
import java.util.List;

public class LifecycleBean implements InitializingBean, DisposableBean {

    static final List<String> EVENTS = new ArrayList<>();

    @Override
    public void afterPropertiesSet() {
        EVENTS.add("afterPropertiesSet");
    }

    public void customInit() {
        EVENTS.add("customInit");
    }

    @Override
    public void destroy() {
        EVENTS.add("destroy");
    }

    public void customDestroy() {
        EVENTS.add("customDestroy");
    }
}
