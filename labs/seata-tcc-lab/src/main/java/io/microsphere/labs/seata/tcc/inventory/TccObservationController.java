package io.microsphere.labs.seata.tcc.inventory;

import io.seata.core.context.RootContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/lab/seata/tcc")
public class TccObservationController {

    private final JdbcTemplate jdbcTemplate;

    public TccObservationController(JdbcTemplate jdbcTemplate) {
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
                "SELECT sku_id, available, reserved, used FROM lab_stock WHERE sku_id = ?", skuId);
    }

    @GetMapping("/fence-log")
    public List<Map<String, Object>> fenceLog() {
        return jdbcTemplate.queryForList(
                "SELECT xid, branch_id, action_name, status, gmt_create, gmt_modified " +
                        "FROM tcc_fence_log ORDER BY gmt_modified DESC LIMIT 20");
    }
}
