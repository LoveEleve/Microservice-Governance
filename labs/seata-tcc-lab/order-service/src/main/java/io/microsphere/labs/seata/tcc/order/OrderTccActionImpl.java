package io.microsphere.labs.seata.tcc.order;

import io.seata.rm.tcc.api.BusinessActionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderTccActionImpl implements OrderTccAction {

    private final JdbcTemplate jdbcTemplate;

    public OrderTccActionImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean tryCreate(BusinessActionContext context,
                             String orderId,
                             String skuId,
                             String userId,
                             int count) {
        int updated = jdbcTemplate.update(
                "INSERT INTO lab_order (order_id, sku_id, user_id, count, status) VALUES (?, ?, ?, ?, ?)",
                orderId, skuId, userId, count, "CREATED");
        return updated == 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirm(BusinessActionContext context) {
        String orderId = context.getActionContext("orderId", String.class);
        jdbcTemplate.update(
                "UPDATE lab_order SET status = 'CONFIRMED' WHERE order_id = ? AND status = 'CREATED'",
                orderId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(BusinessActionContext context) {
        String orderId = context.getActionContext("orderId", String.class);
        jdbcTemplate.update(
                "UPDATE lab_order SET status = 'CANCELLED' WHERE order_id = ? AND status = 'CREATED'",
                orderId);
        return true;
    }
}
