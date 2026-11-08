package com.wuba.crm.tx.tcc.starter.aspect;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TccExpressionResolverTest {

    private final TccExpressionResolver resolver = resolver();

    @Test
    void templateKeepsLiteralHash() {
        assertEquals("order:u1", resolve("order:#{#a0}"));
    }

    @Test
    void templateSupportsSpelInside() {
        assertEquals("quota:u1", resolve("#{'quota:' + #a0}"));
    }

    @Test
    void templateSupportsParameterNames() {
        assertEquals("order:o1", resolve("order:#{#orderId}"));
    }

    @Test
    void legacyWholeExpressionStillWorks() {
        assertEquals("u1", resolve("#a0"));
        assertEquals("quota:u1", resolve("'quota:' + #a0"));
    }

    @Test
    void plainLiteralHashIsNotParsed() {
        assertEquals("order:#123", resolve("order:#123"));
    }

    @Test
    void quotedLiteralHashStaysLiteral() {
        assertEquals("#123", resolve("'#123'"));
    }

    @Test
    void placeholdersAreResolvedBeforeSpel() {
        assertEquals("127.0.0.1:9092", resolve("${quota.endpoint}"));
    }

    private String resolve(String value) {
        try {
            Method method = Sample.class.getDeclaredMethod("sample", String.class, String.class);
            return resolver.resolve(value, method, new Object[]{"u1", "o1"});
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private TccExpressionResolver resolver() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "test", Map.of("quota.endpoint", "127.0.0.1:9092")));
        TccExpressionResolver resolver = new TccExpressionResolver();
        resolver.setEnvironment(environment);
        return resolver;
    }

    static class Sample {
        void sample(String userId, String orderId) {
        }
    }
}
