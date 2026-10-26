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
package io.github.loveeleve.polygateway.gateway;

import io.github.loveeleve.polygateway.web.WebEndpointMapping;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.FilterDefinition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PolyGatewayFilterRule} Test
 *
 * @author <a href="https://github.com/LoveEleve">LoveEleve</a>
 * @since 1.0.0
 */
class PolyGatewayFilterRuleTest {

    @Test
    void testEmptyRuleMatchesEveryEndpoint() {
        PolyGatewayFilterRule rule = new PolyGatewayFilterRule();
        rule.setFilters(List.of(new FilterDefinition("AddResponseHeader=X-Rule,on")));

        assertTrue(rule.matches("order-service", endpoint("/orders/{id}", "GET")));
        assertTrue(rule.matches("pay-service", endpoint("/internal/reconcile", "POST")));
    }

    @Test
    void testServiceScoping() {
        PolyGatewayFilterRule rule = new PolyGatewayFilterRule();
        rule.setServices("order-service");

        assertTrue(rule.matches("order-service", endpoint("/orders", "GET")));
        assertFalse(rule.matches("pay-service", endpoint("/orders", "GET")));
        assertFalse(rule.matches(null, endpoint("/orders", "GET")));
    }

    @Test
    void testPatternScoping() {
        PolyGatewayFilterRule rule = new PolyGatewayFilterRule();
        rule.setPatterns("/orders/**");

        assertTrue(rule.matches("order-service", endpoint("/orders/{id}", "GET")));
        assertTrue(rule.matches("order-service", endpoint("/orders/special", "GET")));
        assertFalse(rule.matches("order-service", endpoint("/internal/reconcile", "POST")));
    }

    @Test
    void testMethodScoping() {
        PolyGatewayFilterRule rule = new PolyGatewayFilterRule();
        rule.setMethods("GET");

        assertTrue(rule.matches("order-service", endpoint("/orders", "GET")));
        assertFalse(rule.matches("order-service", endpoint("/orders", "POST")));
        // The endpoint declares no method condition, it accepts any method
        assertTrue(rule.matches("order-service", endpoint("/orders")));
    }

    @Test
    void testEveryDimensionMustMatch() {
        PolyGatewayFilterRule rule = new PolyGatewayFilterRule();
        rule.setServices("order-service");
        rule.setPatterns("/orders/**");

        assertTrue(rule.matches("order-service", endpoint("/orders/{id}", "GET")));
        assertFalse(rule.matches("pay-service", endpoint("/orders/{id}", "GET")));
        assertFalse(rule.matches("order-service", endpoint("/internal/reconcile", "POST")));
    }

    private static WebEndpointMapping endpoint(String pattern, String... methods) {
        return WebEndpointMapping.builder(WebEndpointMapping.Kind.MVC)
                .patterns(pattern)
                .methods(methods)
                .build();
    }
}
