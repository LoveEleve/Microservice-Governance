package io.microsphere.labs.spring.framework;

public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public String createOrder(String orderId) {
        return orderRepository.save(orderId);
    }
}
