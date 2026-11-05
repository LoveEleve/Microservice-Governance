package io.microsphere.labs.seata.at.order;

import io.seata.core.context.RootContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/lab/seata/order")
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
                "SELECT order_id, sku_id, user_id, amount, status FROM lab_order WHERE order_id = ?", orderId);
    }

    @GetMapping("/undo-log")
    public List<Map<String, Object>> undoLog() {
        return jdbcTemplate.queryForList(
                "SELECT id, branch_id, xid, log_status, log_created, log_modified FROM undo_log ORDER BY id DESC LIMIT 20");
    }
}
