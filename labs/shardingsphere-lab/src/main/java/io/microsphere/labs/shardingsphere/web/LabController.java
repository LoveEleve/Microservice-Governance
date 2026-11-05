package io.microsphere.labs.shardingsphere.web;

import io.microsphere.labs.shardingsphere.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/lab/sharding")
public class LabController {

    private final OrderService orderService;

    public LabController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public Map<String, Object> createOrder(@RequestParam("userId") long userId,
                                           @RequestParam("amount") BigDecimal amount,
                                           @RequestParam(value = "product", defaultValue = "book") String product,
                                           @RequestParam(value = "quantity", defaultValue = "1") int quantity) {
        long orderId = orderService.createOrderWithItem(userId, amount, product, quantity);
        return Map.of("orderId", orderId);
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> findOrders(@RequestParam("userId") long userId) {
        return orderService.findOrdersByUserId(userId);
    }

    @GetMapping("/orders/{orderId}")
    public List<Map<String, Object>> findOrder(@PathVariable("orderId") long orderId) {
        return orderService.findOrderByOrderId(orderId);
    }

    @GetMapping("/items")
    public List<Map<String, Object>> findItems(@RequestParam("userId") long userId) {
        return orderService.findItemsByUserId(userId);
    }

    @PostMapping("/dict")
    public Map<String, Object> upsertDict(@RequestParam("type") String type,
                                          @RequestParam("value") String value) {
        orderService.upsertDict(type, value);
        return Map.of("ok", true);
    }

    @GetMapping("/dict")
    public List<Map<String, Object>> findDict(@RequestParam("type") String type) {
        return orderService.findDict(type);
    }
}
