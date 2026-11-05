package io.microsphere.labs.shardingsphere.service;

import io.microsphere.labs.shardingsphere.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public long createOrderWithItem(long userId, BigDecimal amount, String productName, int quantity) {
        long orderId = orderRepository.createOrder(userId, amount);
        orderRepository.createItem(orderId, userId, productName, quantity);
        return orderId;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findOrdersByUserId(long userId) {
        return orderRepository.findOrdersByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findOrderByOrderId(long orderId) {
        return orderRepository.findOrderByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findItemsByUserId(long userId) {
        return orderRepository.findItemsByUserId(userId);
    }

    @Transactional
    public void upsertDict(String dictType, String dictValue) {
        orderRepository.upsertDict(dictType, dictValue);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findDict(String dictType) {
        return orderRepository.findDict(dictType);
    }
}
