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
package io.github.loveeleve.polydata.context;

import io.github.loveeleve.polydata.config.DynamicJdbcConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.GenericApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the parent-context listener registration of
 * {@link DynamicJdbcChildContextRefreshedListener}.
 *
 * @author <a href="https://github.com/LoveEleve">LoveEleve</a>
 * @since 1.0.0
 */
class DynamicJdbcChildContextRefreshedListenerTest {

    @Test
    void testDataSourceChildContextDoesNotRegisterParentCloseListener() {
        try (GenericApplicationContext parent = new GenericApplicationContext();
             GenericApplicationContext child = new GenericApplicationContext()) {
            parent.refresh();
            int listenersBefore = parent.getApplicationListeners().size();

            DynamicJdbcConfig config = config("datasource-child");
            DynamicJdbcChildContextRefreshedListener listener =
                    new DynamicJdbcChildContextRefreshedListener(config, parent, child.getBeanFactory(), false);
            listener.onApplicationEvent(new ContextRefreshedEvent(child));

            assertEquals(listenersBefore, parent.getApplicationListeners().size());
        }
    }

    @Test
    void testConfigChildContextRegistersParentCloseListener() {
        try (GenericApplicationContext parent = new GenericApplicationContext();
             GenericApplicationContext child = new GenericApplicationContext()) {
            parent.refresh();
            int listenersBefore = parent.getApplicationListeners().size();

            DynamicJdbcConfig config = config("config-child");
            child.refresh();
            child.getBeanFactory().registerSingleton("dynamicJdbcConfig", config);

            DynamicJdbcChildContextRefreshedListener listener =
                    new DynamicJdbcChildContextRefreshedListener(config, parent, child.getBeanFactory(), true);
            listener.onApplicationEvent(new ContextRefreshedEvent(child));

            assertEquals(listenersBefore + 1, parent.getApplicationListeners().size());
        }
    }

    private static DynamicJdbcConfig config(String name) {
        DynamicJdbcConfig config = new DynamicJdbcConfig();
        config.setName(name);
        return config;
    }
}
