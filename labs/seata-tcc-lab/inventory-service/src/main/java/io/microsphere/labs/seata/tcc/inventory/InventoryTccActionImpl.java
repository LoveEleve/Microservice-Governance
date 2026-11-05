package io.microsphere.labs.seata.tcc.inventory;

import io.seata.rm.tcc.api.BusinessActionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryTccActionImpl implements InventoryTccAction {

    private final JdbcTemplate jdbcTemplate;

    public InventoryTccActionImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean tryDeduct(BusinessActionContext context, String skuId, int count) {
        int updated = jdbcTemplate.update(
                "UPDATE lab_stock SET available = available - ?, reserved = reserved + ? " +
                        "WHERE sku_id = ? AND available >= ?",
                count, count, skuId, count);
        return updated == 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirm(BusinessActionContext context) {
        String skuId = context.getActionContext("skuId", String.class);
        Integer count = context.getActionContext("count", Integer.class);

        jdbcTemplate.update(
                "UPDATE lab_stock SET reserved = reserved - ?, used = used + ? " +
                        "WHERE sku_id = ? AND reserved >= ?",
                count, count, skuId, count);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(BusinessActionContext context) {
        String skuId = context.getActionContext("skuId", String.class);
        Integer count = context.getActionContext("count", Integer.class);

        jdbcTemplate.update(
                "UPDATE lab_stock SET reserved = reserved - ?, available = available + ? " +
                        "WHERE sku_id = ? AND reserved >= ?",
                count, count, skuId, count);
        return true;
    }
}
