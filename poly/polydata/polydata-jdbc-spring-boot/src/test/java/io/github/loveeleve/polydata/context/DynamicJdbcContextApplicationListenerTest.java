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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the cross-config validation of {@link DynamicJdbcContextApplicationListener}.
 *
 * @author <a href="https://github.com/LoveEleve">LoveEleve</a>
 * @since 1.0.0
 */
class DynamicJdbcContextApplicationListenerTest {

    @Test
    void testUniqueNamesAndSinglePrimaryAreAccepted() {
        Map<String, DynamicJdbcConfig> configs = new LinkedHashMap<>();
        configs.put("polydata.jdbc.configs.one", config("one", false));
        configs.put("polydata.jdbc.configs.two", config("two", true));

        assertDoesNotThrow(() -> new DynamicJdbcContextApplicationListener().validateDynamicJdbcConfigs(configs));
    }

    @Test
    void testDuplicateNamesAreRejected() {
        Map<String, DynamicJdbcConfig> configs = new LinkedHashMap<>();
        configs.put("polydata.jdbc.configs.one", config("same", false));
        configs.put("polydata.jdbc.configs.two", config("same", false));

        assertThrows(IllegalArgumentException.class,
                () -> new DynamicJdbcContextApplicationListener().validateDynamicJdbcConfigs(configs));
    }

    @Test
    void testMultiplePrimaryConfigsAreRejected() {
        Map<String, DynamicJdbcConfig> configs = new LinkedHashMap<>();
        configs.put("polydata.jdbc.configs.one", config("one", true));
        configs.put("polydata.jdbc.configs.two", config("two", true));

        assertThrows(IllegalArgumentException.class,
                () -> new DynamicJdbcContextApplicationListener().validateDynamicJdbcConfigs(configs));
    }

    @Test
    void testNoPrimaryIsAccepted() {
        Map<String, DynamicJdbcConfig> configs = new LinkedHashMap<>();
        configs.put("polydata.jdbc.configs.one", config("one", false));
        configs.put("polydata.jdbc.configs.two", config("two", false));

        assertDoesNotThrow(() -> new DynamicJdbcContextApplicationListener().validateDynamicJdbcConfigs(configs));
    }

    private static DynamicJdbcConfig config(String name, boolean primary) {
        DynamicJdbcConfig config = new DynamicJdbcConfig();
        config.setName(name);
        config.setPrimary(primary);
        return config;
    }
}
