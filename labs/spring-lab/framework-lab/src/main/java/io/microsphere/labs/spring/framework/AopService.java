package io.microsphere.labs.spring.framework;

public class AopService {

    public String work() {
        return internal();
    }

    public String internal() {
        return "internal";
    }
}
