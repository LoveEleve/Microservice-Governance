package io.microsphere.labs.seata.order;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/lab/seata/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public String createOrder(@RequestParam String orderId,
                              @RequestParam String skuId,
                              @RequestParam String userId,
                              @RequestParam int amount) {
        orderService.createOrder(orderId, skuId, userId, amount);
        return "OK";
    }
}
