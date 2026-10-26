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
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.util.AntPathMatcher;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * A rule adding the filters to the generated routes of the matching endpoints. The dimensions are
 * combined with AND. Every omitted (empty) dimension matches everything the other dimensions allow :
 *
 * <ul>
 * <li>{@code services} : the endpoints of the given service ids (exact match); empty = every service</li>
 * <li>{@code patterns} : an endpoint pattern matching any rule pattern (Ant style); empty = every pattern</li>
 * <li>{@code methods} : the endpoint methods intersect the rule methods; an endpoint without method
 * condition accepts any method; empty = every method</li>
 * <li>{@code filters} : the {@link FilterDefinition FilterDefinitions} appended to the route of the
 * matching endpoint (the SCG filter definition syntax, the route keeps its {@code StripPrefix})</li>
 * </ul>
 *
 * <h3>Example Usage</h3>
 * <pre>{@code
 *   polygateway:
 *     gateway:
 *       filter-rules:
 *         - services: [order-service]
 *           patterns: ["/orders/**"]
 *           filters:
 *             - name: RequestRateLimiter
 *               args:
 *                 redis-rate-limiter.replenishRate: 2
 *                 redis-rate-limiter.burstCapacity: 2
 *                 key-resolver: "#{@pathKeyResolver}"
 * }</pre>
 *
 * @author <a href="https://github.com/LoveEleve">LoveEleve</a>
 * @since 1.0.0
 */
public class PolyGatewayFilterRule {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private String[] services;

    private String[] patterns;

    private String[] methods;

    private List<FilterDefinition> filters = new LinkedList<>();

    /**
     * Tests whether the given endpoint of the given service matches this rule.
     *
     * @param serviceId the service id of the endpoint, {@code null} when unknown
     * @param endpoint  the web endpoint
     * @return {@code true} when the filters of this rule must be applied to the route of the endpoint
     */
    public boolean matches(String serviceId, WebEndpointMapping endpoint) {
        return servicesMatch(serviceId)
                && anyPatternMatches(endpoint.getPatterns())
                && methodsMatch(endpoint.getMethods());
    }

    public String[] getServices() {
        return services;
    }

    public void setServices(String... services) {
        this.services = services;
    }

    public String[] getPatterns() {
        return patterns;
    }

    public void setPatterns(String... patterns) {
        this.patterns = patterns;
    }

    public String[] getMethods() {
        return methods;
    }

    public void setMethods(String... methods) {
        this.methods = methods;
    }

    public List<FilterDefinition> getFilters() {
        return filters;
    }

    public void setFilters(List<FilterDefinition> filters) {
        this.filters = filters;
    }

    private boolean servicesMatch(String serviceId) {
        if (isEmpty(services)) {
            // Without services the rule applies to every service
            return true;
        }
        return serviceId != null && contains(services, serviceId);
    }

    private boolean anyPatternMatches(String[] endpointPatterns) {
        if (isEmpty(patterns)) {
            // Without patterns the rule applies to every endpoint pattern
            return true;
        }
        for (String endpointPattern : endpointPatterns) {
            for (String rulePattern : patterns) {
                if (PATH_MATCHER.match(rulePattern, endpointPattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean methodsMatch(String[] endpointMethods) {
        if (isEmpty(methods) || isEmpty(endpointMethods)) {
            // Without rule methods, or the endpoint accepts any method
            return true;
        }
        return intersects(endpointMethods, methods);
    }

    private static boolean contains(String[] values, String value) {
        return values != null && Arrays.stream(values).anyMatch(value::equals);
    }

    private static boolean intersects(String[] endpointValues, String[] ruleValues) {
        for (String ruleValue : ruleValues) {
            if (contains(endpointValues, ruleValue)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEmpty(String[] values) {
        return values == null || values.length == 0;
    }

    @Override
    public String toString() {
        return "PolyGatewayFilterRule{services=" + Arrays.toString(services)
                + ", patterns=" + Arrays.toString(patterns)
                + ", methods=" + Arrays.toString(methods)
                + ", filters=" + filters + '}';
    }
}
