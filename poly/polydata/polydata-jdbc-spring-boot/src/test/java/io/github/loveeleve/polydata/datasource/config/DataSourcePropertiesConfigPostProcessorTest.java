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
package io.github.loveeleve.polydata.datasource.config;

import io.github.loveeleve.polydata.config.DynamicJdbcConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static io.github.loveeleve.polydata.util.DynamicJdbcConfigUtils.parseDynamicJdbcConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link DataSourcePropertiesConfigPostProcessor}.
 *
 * @author <a href="https://github.com/LoveEleve">LoveEleve</a>
 * @since 1.0.0
 */
class DataSourcePropertiesConfigPostProcessorTest {

    @Test
    void testAllHighAvailabilityZonesArePostProcessed() throws Exception {
        DynamicJdbcConfig config = parseDynamicJdbcConfig("polydata.jdbc.configs.primary",
                "{\"name\":\"ha\",\"ha-datasource\":{"
                        + "\"defaultZone\":[{\"name\":\"ds\",\"url\":\"jdbc:h2:mem:default;DB_CLOSE_DELAY=-1\"}],"
                        + "\"test-zone\":[{\"name\":\"ds\",\"url\":\"jdbc:h2:mem:test;DB_CLOSE_DELAY=-1\"}]}}");

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "polydata.jdbc.modules.datasource.default-user-name", "sa",
                "polydata.jdbc.modules.datasource.default-password", "sa")));

        DataSourcePropertiesConfigPostProcessor postProcessor = new DataSourcePropertiesConfigPostProcessor();
        postProcessor.setEnvironment(environment);
        postProcessor.afterPropertiesSet();
        postProcessor.postProcess(config, "polydata.jdbc.configs.primary");

        config.getHighAvailabilityDataSourcePropertiesMap().forEach((zone, dataSourcePropertiesList) -> {
            Map<String, String> properties = dataSourcePropertiesList.get(0);
            assertEquals("com.zaxxer.hikari.HikariDataSource", properties.get("type"), zone);
            assertEquals("org.h2.Driver", properties.get("driverClassName"), zone);
            assertEquals("sa", properties.get("username"), zone);
            assertEquals("sa", properties.get("password"), zone);
        });
    }
}
