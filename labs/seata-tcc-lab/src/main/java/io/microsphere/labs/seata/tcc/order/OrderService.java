package io.microsphere.labs.seata.tcc.order;

import io.microsphere.labs.seata.tcc.inventory.InventoryTccAction;
import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final JdbcTemplate jdbcTemplate;
    private final InventoryTccAction inventoryTccAction;

    public OrderService(JdbcTemplate jdbcTemplate, InventoryTccAction inventoryTccAction) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryTccAction = inventoryTccAction;
    }

    @GlobalTransactional(name = "tcc-create-order", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(String orderId, String skuId, String userId, int count) {
        jdbcTemplate.update(
                "INSERT INTO lab_order (order_id, sku_id, user_id, count, status) VALUES (?, ?, ?, ?, ?)",
                orderId, skuId, userId, count, "CREATED");

        boolean reserved = inventoryTccAction.tryDeduct(new BusinessActionContext(), skuId, count);
        if (!reserved) {
            throw new IllegalStateException("stock is not enough: " + skuId);
        }
    }
}
