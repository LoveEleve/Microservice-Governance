package io.microsphere.labs.seata.inventory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final JdbcTemplate jdbcTemplate;

    public InventoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deduct(String skuId, int count) {
        int updated = jdbcTemplate.update(
                "UPDATE lab_stock SET quantity = quantity - ? WHERE sku_id = ? AND quantity >= ?",
                count, skuId, count);

        if (updated != 1) {
            throw new IllegalStateException("stock is not enough: " + skuId);
        }
    }
}
