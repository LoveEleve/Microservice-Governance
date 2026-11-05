package io.microsphere.labs.seata.tcc.order;

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

    @GlobalTransactional(name = "tcc-create-order", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(String orderId, String skuId, String userId, int count) {
        jdbcTemplate.update(
                "INSERT INTO lab_order (order_id, sku_id, user_id, count, status) VALUES (?, ?, ?, ?, ?)",
                orderId, skuId, userId, count, "CREATED");

        Boolean reserved = restTemplate.postForObject(
                "http://127.0.0.1:18084/internal/tcc/inventory/try?skuId={skuId}&count={count}",
                null, Boolean.class, skuId, count);

        if (!Boolean.TRUE.equals(reserved)) {
            throw new IllegalStateException("stock is not enough: " + skuId);
        }
    }
}
