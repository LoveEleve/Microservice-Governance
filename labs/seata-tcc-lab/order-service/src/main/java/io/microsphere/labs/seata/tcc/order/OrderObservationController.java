package io.microsphere.labs.seata.tcc.order;

import io.seata.core.context.RootContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/lab/seata/tcc/order")
public class OrderObservationController {

    private final JdbcTemplate jdbcTemplate;

    public OrderObservationController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/xid")
    public String currentXid() {
        String xid = RootContext.getXID();
        return xid == null ? "NO_GLOBAL_TRANSACTION" : xid;
    }

    @GetMapping("/detail/{orderId}")
    public Map<String, Object> order(@PathVariable String orderId) {
        return jdbcTemplate.queryForMap(
                "SELECT order_id, sku_id, user_id, count, status FROM lab_order WHERE order_id = ?", orderId);
    }
}
