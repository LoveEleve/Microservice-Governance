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

package io.microsphere.alibaba.sentinel.spring.boot.autoconfigure;


import io.microsphere.alibaba.sentinel.redis.spring.SentinelRedisCommandInterceptor;
import io.microsphere.alibaba.sentinel.spring.boot.autoconfigure.SentinelRedisAutoConfiguration.Config;
import io.microsphere.redis.metadata.RedisMetadataLoader;
import io.microsphere.redis.spring.interceptor.RedisMethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;

import java.util.Set;

/**
 * {@link SentinelRedisAutoConfiguration} Test
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @see SentinelRedisAutoConfiguration
 * @since 1.0.0
 */
class SentinelRedisAutoConfigurationTest extends AutoConfigurationTest<SentinelRedisAutoConfiguration> {

    @Test
    void tesOntDisabledProperty() {
        assertDisabledProperty("microsphere.redis.interceptor.enabled=false",
                Config.class, SentinelRedisCommandInterceptor.class);
    }

    @Override
    protected void configureAutoConfiguredClasses(Set<Class<?>> autoConfiguredClasses) {
        autoConfiguredClasses.add(Config.class);
        autoConfiguredClasses.add(SentinelRedisCommandInterceptor.class);
    }

    @Override
    protected void configureGlobalDisabledPropertyValues(Set<String> globalDisabledPropertyValues) {
        globalDisabledPropertyValues.add("microsphere.redis.enabled=false");
        globalDisabledPropertyValues.add("microsphere.sentinel.redis.enabled=false");
    }

    @Override
    protected void configureGlobalMissingClasses(Set<Class<?>> globalMissingClasses) {
        globalMissingClasses.add(RedisConnection.class);
        globalMissingClasses.add(RedisMetadataLoader.class);
        globalMissingClasses.add(RedisMethodInterceptor.class);
        globalMissingClasses.add(SentinelRedisCommandInterceptor.class);
    }
}