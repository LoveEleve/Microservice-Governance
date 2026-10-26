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
package io.github.loveeleve.polygateway.provider;

import io.github.loveeleve.polygateway.web.WebEndpointMapping;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * {@link WebEndpointMappingsCollector} Test
 *
 * @author <a href="https://github.com/LoveEleve">LoveEleve</a>
 * @since 1.0.0
 */
class WebEndpointMappingsCollectorTest {

    @Configuration
    @EnableWebMvc
    static class WebConfig {
    }

    @RestController
    static class TestController {

        @GetMapping("/orders/{id}")
        public String getOrder(@PathVariable String id) {
            return id;
        }

        @PostMapping(path = "/internal/reconcile", produces = "application/json")
        public String reconcile() {
            return "{}";
        }

        @RequestMapping("/any-method")
        public String anyMethod() {
            return "";
        }
    }

    @RestController
    static class ExtraController {

        @GetMapping("/aaa/extra")
        public String extra() {
            return "";
        }
    }

    @Test
    void testCollect() {
        List<WebEndpointMapping> mappings = collect(TestController.class);

        WebEndpointMapping orders = find(mappings, "/orders/{id}");
        assertArrayEquals(new String[]{"GET"}, orders.getMethods());
        assertEquals(WebEndpointMapping.Kind.MVC, orders.getKind());

        WebEndpointMapping reconcile = find(mappings, "/internal/reconcile");
        assertArrayEquals(new String[]{"POST"}, reconcile.getMethods());
        assertArrayEquals(new String[]{"application/json"}, reconcile.getProduces());

        WebEndpointMapping anyMethod = find(mappings, "/any-method");
        assertEquals(0, anyMethod.getMethods().length);

        assertNotEquals(0, orders.getId());
        assertNotEquals(orders.getId(), reconcile.getId());

        // The deterministic order is stable for the same endpoint set
        assertEquals(mappings, collect(TestController.class));
    }

    @Test
    void testIdsAreStableWhenTheEndpointSetChanges() {
        WebEndpointMapping orders = find(collect(TestController.class), "/orders/{id}");
        WebEndpointMapping ordersWithExtra = find(collect(TestController.class, ExtraController.class), "/orders/{id}");
        WebEndpointMapping extra = find(collect(TestController.class, ExtraController.class), "/aaa/extra");

        assertEquals(orders.getId(), ordersWithExtra.getId());
        assertNotEquals(orders.getId(), extra.getId());
    }

    @Test
    void testInfrastructureControllersAreSkipped() {
        WebEndpointMappingsCollector collector = new WebEndpointMappingsCollector();

        assertFalse(collector.isIncludedController("org.springframework.boot.actuate.endpoint.web.servlet.WebMvcEndpointHandlerMapping"));
        assertFalse(collector.isIncludedController("org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController"));
    }

    private static List<WebEndpointMapping> collect(Class<?>... controllers) {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebConfig.class);
        context.register(controllers);
        context.refresh();
        try {
            return new WebEndpointMappingsCollector().collect(context);
        } finally {
            context.close();
        }
    }

    private static WebEndpointMapping find(List<WebEndpointMapping> mappings, String pattern) {
        return mappings.stream()
                .filter(mapping -> Arrays.asList(mapping.getPatterns()).contains(pattern))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No mapping was collected for the pattern " + pattern));
    }
}
