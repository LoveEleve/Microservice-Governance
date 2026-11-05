package io.microsphere.labs.seata.saga.travel;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/lab/seata/saga")
public class SagaObservationController {

    private final JdbcTemplate jdbcTemplate;

    public SagaObservationController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/bookings")
    public Map<String, Object> bookings() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("flight", jdbcTemplate.queryForList("SELECT user_id, status FROM flight_booking"));
        result.put("hotel", jdbcTemplate.queryForList("SELECT user_id, status FROM hotel_booking"));
        result.put("car", jdbcTemplate.queryForList("SELECT user_id, status FROM car_booking"));
        return result;
    }

    @GetMapping("/state-instances")
    public List<Map<String, Object>> stateInstances() {
        return jdbcTemplate.queryForList(
                "SELECT id, machine_id, status, compensation_status, gmt_started, gmt_end " +
                        "FROM seata_state_machine_inst ORDER BY gmt_started DESC LIMIT 20");
    }

    @GetMapping("/state-nodes")
    public List<Map<String, Object>> stateNodes() {
        return jdbcTemplate.queryForList(
                "SELECT machine_inst_id, name, status, service_name, service_method " +
                        "FROM seata_state_inst ORDER BY gmt_started DESC LIMIT 50");
    }
}
