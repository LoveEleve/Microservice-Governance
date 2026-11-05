package io.microsphere.labs.seata.order;

import io.microsphere.labs.seata.account.AccountService;
import io.microsphere.labs.seata.inventory.InventoryService;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final JdbcTemplate jdbcTemplate;
    private final InventoryService inventoryService;
    private final AccountService accountService;

    public OrderService(JdbcTemplate jdbcTemplate,
                        InventoryService inventoryService,
                        AccountService accountService) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryService = inventoryService;
        this.accountService = accountService;
    }

    @GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(String orderId, String skuId, String userId, int amount) {
        jdbcTemplate.update(
                "INSERT INTO lab_order (order_id, sku_id, user_id, amount, status) VALUES (?, ?, ?, ?, ?)",
                orderId, skuId, userId, amount, "CREATED");

        inventoryService.deduct(skuId, 1);
        accountService.deduct(userId, amount);
    }
}
