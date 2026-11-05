package io.microsphere.labs.seata.at;

import io.seata.core.context.RootContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/lab/seata/at")
public class AtObservationController {

    private final JdbcTemplate jdbcTemplate;

    public AtObservationController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/xid")
    public String currentXid() {
        String xid = RootContext.getXID();
        return xid == null ? "NO_GLOBAL_TRANSACTION" : xid;
    }

    @GetMapping("/stock/{skuId}")
    public Map<String, Object> stock(@PathVariable String skuId) {
        return jdbcTemplate.queryForMap(
                "SELECT sku_id, quantity FROM lab_stock WHERE sku_id = ?", skuId);
    }

    @GetMapping("/account/{userId}")
    public Map<String, Object> account(@PathVariable String userId) {
        return jdbcTemplate.queryForMap(
                "SELECT user_id, balance FROM lab_account WHERE user_id = ?", userId);
    }

    @GetMapping("/undo-log")
    public List<Map<String, Object>> undoLog() {
        return jdbcTemplate.queryForList(
                "SELECT id, branch_id, xid, log_status, log_created, log_modified FROM undo_log ORDER BY id DESC LIMIT 20");
    }
}
