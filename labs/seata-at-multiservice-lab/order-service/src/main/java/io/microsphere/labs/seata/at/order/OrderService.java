package io.microsphere.labs.seata.at.order;

import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
public class OrderService {

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;

    public OrderService(JdbcTemplate jdbcTemplate, RestTemplate restTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.restTemplate = restTemplate;
    }

    @GlobalTransactional(name = "at-create-order", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(String orderId, String skuId, String userId, int amount) {
        jdbcTemplate.update(
                "INSERT INTO lab_order (order_id, sku_id, user_id, amount, status) VALUES (?, ?, ?, ?, ?)",
                orderId, skuId, userId, amount, "CREATED");

        restTemplate.postForObject(
                "http://127.0.0.1:18081/internal/inventory/deduct?skuId={skuId}&count={count}",
                null, String.class, skuId, 1);

        restTemplate.postForObject(
                "http://127.0.0.1:18082/internal/account/deduct?userId={userId}&amount={amount}",
                null, String.class, userId, amount);
    }
}
