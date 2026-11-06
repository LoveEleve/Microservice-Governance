package com.wuba.crm.tx.demo;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

/**
 * 从业务方 application.yml 加载配置。
 *
 * <p>真实 Spring Boot 应用里可以直接用 @ConfigurationProperties；
 * demo 模块不启动 Boot，所以用 Binder 显式绑定。</p>
 */
public final class DemoConfigLoader {

    private DemoConfigLoader() {
    }

    public static DemoProperties load(Resource resource) throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("tx-demo", resource);
        MutablePropertySources propertySources = new MutablePropertySources();
        for (PropertySource<?> source : sources) {
            propertySources.addLast(source);
        }
        Binder binder = new Binder(ConfigurationPropertySources.from(propertySources));
        return binder.bind("tx", Bindable.of(DemoProperties.class))
                .orElseThrow(() -> new IllegalStateException("missing tx configuration"));
    }
}
