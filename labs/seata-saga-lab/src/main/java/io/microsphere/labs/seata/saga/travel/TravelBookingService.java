package io.microsphere.labs.seata.saga.travel;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TravelBookingService {

    private final JdbcTemplate jdbcTemplate;

    public TravelBookingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bookFlight(Map<String, Object> input) {
        return book("flight_booking", "flight", input);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancelFlight(Map<String, Object> input) {
        return cancel("flight_booking", "flight", input);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bookHotel(Map<String, Object> input) {
        return book("hotel_booking", "hotel", input);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancelHotel(Map<String, Object> input) {
        return cancel("hotel_booking", "hotel", input);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bookCar(Map<String, Object> input) {
        if (Boolean.TRUE.equals(input.get("failCar"))) {
            throw new IllegalStateException("car booking failed");
        }
        return book("car_booking", "car", input);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancelCar(Map<String, Object> input) {
        return cancel("car_booking", "car", input);
    }

    private Map<String, Object> book(String table, String type, Map<String, Object> input) {
        String userId = String.valueOf(input.get("userId"));
        jdbcTemplate.update(
                "INSERT INTO " + table + " (user_id, status) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE status = VALUES(status)",
                userId, "BOOKED");
        return result(type, userId, "BOOKED");
    }

    private Map<String, Object> cancel(String table, String type, Map<String, Object> input) {
        String userId = String.valueOf(input.get("userId"));
        jdbcTemplate.update(
                "UPDATE " + table + " SET status = ? WHERE user_id = ?",
                "CANCELLED", userId);
        return result(type, userId, "CANCELLED");
    }

    private Map<String, Object> result(String type, String userId, String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bookingId", type + ":" + userId);
        result.put("status", status);
        return result;
    }
}
