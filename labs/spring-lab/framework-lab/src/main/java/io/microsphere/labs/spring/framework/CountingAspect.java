package io.microsphere.labs.spring.framework;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

import java.util.concurrent.atomic.AtomicInteger;

@Aspect
public class CountingAspect {

    private static final AtomicInteger COUNT = new AtomicInteger();

    public static void reset() {
        COUNT.set(0);
    }

    public static int count() {
        return COUNT.get();
    }

    @Before("execution(* io.microsphere.labs.spring.framework.AopService.internal(..))")
    public void beforeInternal() {
        COUNT.incrementAndGet();
    }
}
