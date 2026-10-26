/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loveeleve.polyconfig.nacos.annotation;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import io.github.loveeleve.polyconfig.spring.event.ConfigPropertiesChangedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.type.AnnotationMetadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link NacosPropertySourceLoader}
 *
 * @author <a href="https://github.com/LoveEleve">LoveEleve</a>
 * @since 1.0.0
 */
class NacosPropertySourceLoaderTest {

    @Test
    void testLoadPropertySource() throws Exception {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getConfig("app.properties", "DEFAULT_GROUP", 3000L))
                .thenReturn("app.name=nacos-app\napp.port=8080");

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            NacosPropertySourceLoader loader = new TestNacosPropertySourceLoader(configService);
            loader.setEnvironment(context.getEnvironment());
            loader.setResourceLoader(context);
            loader.setBeanFactory(context.getBeanFactory());

            loader.selectImports(AnnotationMetadata.introspect(NacosTestConfig.class));

            assertEquals("nacos-app", context.getEnvironment().getProperty("app.name"));
            assertEquals("8080", context.getEnvironment().getProperty("app.port"));
        }
    }

    @Test
    void testAutoRefreshUpdatesEnvironmentAndPublishesEvent() throws Exception {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getConfig("app.properties", "DEFAULT_GROUP", 3000L)).thenReturn("app.name=nacos-app");

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            NacosPropertySourceLoader loader = new TestNacosPropertySourceLoader(configService);
            loader.setEnvironment(context.getEnvironment());
            loader.setResourceLoader(context);
            loader.setBeanFactory(context.getBeanFactory());

            List<ConfigPropertiesChangedEvent> events = new ArrayList<>();
            context.addApplicationListener(event -> {
                if (event instanceof ConfigPropertiesChangedEvent) {
                    events.add((ConfigPropertiesChangedEvent) event);
                }
            });

            loader.selectImports(AnnotationMetadata.introspect(NacosTestConfig.class));
            assertEquals("nacos-app", context.getEnvironment().getProperty("app.name"));

            ArgumentCaptor<Listener> listenerCaptor = ArgumentCaptor.forClass(Listener.class);
            verify(configService).addListener(eq("app.properties"), eq("DEFAULT_GROUP"), listenerCaptor.capture());

            // Simulate the Nacos server push
            listenerCaptor.getValue().receiveConfigInfo("app.name=nacos-app-v2");

            assertEquals("nacos-app-v2", context.getEnvironment().getProperty("app.name"));
            assertEquals(1, events.size());
            assertTrue(events.get(0).isChanged("app.name"));
            assertEquals("nacos-app", events.get(0).getOldProperties().get("app.name"));
            assertEquals("nacos-app-v2", events.get(0).getNewProperties().get("app.name"));
        }
    }

    @Test
    void testYamlConfigContent() throws Exception {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getConfig("app.yml", "TEST_GROUP", 5000L)).thenReturn("app:\n  name: nacos-yaml");

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            NacosPropertySourceLoader loader = new TestNacosPropertySourceLoader(configService);
            loader.setEnvironment(context.getEnvironment());
            loader.setResourceLoader(context);
            loader.setBeanFactory(context.getBeanFactory());

            loader.selectImports(AnnotationMetadata.introspect(NacosYamlTestConfig.class));

            assertEquals("nacos-yaml", context.getEnvironment().getProperty("app.name"));
        }
    }

    @Test
    void testXmlConfigContent() throws Exception {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getConfig("app.xml", "DEFAULT_GROUP", 3000L)).thenReturn(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\n"
                        + "<properties><entry key=\"app.name\">nacos-xml</entry></properties>");

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            NacosPropertySourceLoader loader = new TestNacosPropertySourceLoader(configService);
            loader.setEnvironment(context.getEnvironment());
            loader.setResourceLoader(context);
            loader.setBeanFactory(context.getBeanFactory());

            loader.selectImports(AnnotationMetadata.introspect(NacosXmlTestConfig.class));

            assertEquals("nacos-xml", context.getEnvironment().getProperty("app.name"));
        }
    }

    @Test
    void testConfigServiceCacheKeyIncludesPassword() throws Exception {
        CachingTestNacosPropertySourceLoader loader = new CachingTestNacosPropertySourceLoader();
        String serverAddress = "127.0.0.1:" + System.nanoTime();
        AnnotationAttributes p1 = nacosAttributes(serverAddress, "user", "p1");
        AnnotationAttributes p1Again = nacosAttributes(serverAddress, "user", "p1");
        AnnotationAttributes p2 = nacosAttributes(serverAddress, "user", "p2");

        ConfigService first = loader.getConfigService(p1);
        ConfigService same = loader.getConfigService(p1Again);
        ConfigService different = loader.getConfigService(p2);

        assertSame(first, same);
        assertNotSame(first, different);
        assertEquals(2, loader.created);
    }

    private static AnnotationAttributes nacosAttributes(String serverAddress, String username, String password) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("serverAddress", serverAddress);
        attributes.put("namespace", "");
        attributes.put("username", username);
        attributes.put("password", password);
        attributes.put("group", "DEFAULT_GROUP");
        attributes.put("timeout", 3000L);
        return new AnnotationAttributes(attributes);
    }

    @NacosPropertySource(value = "app.properties", serverAddress = "127.0.0.1:8848")
    static class NacosTestConfig {
    }

    @NacosPropertySource(value = "app.yml", group = "TEST_GROUP", timeout = 5000L)
    static class NacosYamlTestConfig {
    }

    @NacosPropertySource(value = "app.xml", serverAddress = "127.0.0.1:8848")
    static class NacosXmlTestConfig {
    }

    static class TestNacosPropertySourceLoader extends NacosPropertySourceLoader {

        private final ConfigService configService;

        TestNacosPropertySourceLoader(ConfigService configService) {
            this.configService = configService;
        }

        @Override
        protected ConfigService getConfigService(AnnotationAttributes attributes) {
            return configService;
        }
    }

    static class CachingTestNacosPropertySourceLoader extends NacosPropertySourceLoader {

        private int created;

        @Override
        protected ConfigService createConfigService(AnnotationAttributes attributes) {
            created++;
            return mock(ConfigService.class);
        }
    }
}
