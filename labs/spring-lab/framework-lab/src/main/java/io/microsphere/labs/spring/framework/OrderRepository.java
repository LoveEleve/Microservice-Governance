package io.microsphere.labs.spring.framework;

public class OrderRepository {

    public String save(String orderId) {
        return "saved:" + orderId;
    }
}
