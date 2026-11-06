package io.microsphere.labs.spring.framework;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdkDynamicProxyTest {

    interface GreetingService {
        String greet(String name);
    }

    static class GreetingServiceImpl implements GreetingService {
        @Override
        public String greet(String name) {
            return "hello " + name;
        }
    }

    static class CountingHandler implements InvocationHandler {
        private final GreetingService target;
        private final AtomicInteger count = new AtomicInteger();

        CountingHandler(GreetingService target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            count.incrementAndGet();
            return method.invoke(target, args);
        }
    }

    @Test
    void invokesHandlerOnlyWhenCallingProxy() {
        GreetingService target = new GreetingServiceImpl();
        CountingHandler handler = new CountingHandler(target);
        GreetingService proxy = (GreetingService) Proxy.newProxyInstance(
                GreetingService.class.getClassLoader(),
                new Class<?>[] {GreetingService.class},
                handler);

        assertEquals("hello proxy", proxy.greet("proxy"));
        assertEquals(1, handler.count.get());

        assertEquals("hello target", target.greet("target"));
        assertEquals(1, handler.count.get());
    }
}
