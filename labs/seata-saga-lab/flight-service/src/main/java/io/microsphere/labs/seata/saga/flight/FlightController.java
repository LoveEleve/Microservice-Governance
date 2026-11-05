package io.microsphere.labs.seata.saga.flight;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/internal/flight")
public class FlightController {

    private final Map<String, String> bookings = new ConcurrentHashMap<>();

    @PostMapping("/book")
    public Map<String, Object> book(@RequestBody Map<String, Object> input) {
        String bookingId = "flight:" + input.get("userId");
        bookings.put(bookingId, "BOOKED");
        return result(bookingId, "BOOKED");
    }

    @PostMapping("/cancel")
    public Map<String, Object> cancel(@RequestBody Map<String, Object> input) {
        String bookingId = "flight:" + input.get("userId");
        bookings.put(bookingId, "CANCELLED");
        return result(bookingId, "CANCELLED");
    }

    @GetMapping("/bookings")
    public Map<String, String> bookings() {
        return new LinkedHashMap<>(bookings);
    }

    private Map<String, Object> result(String bookingId, String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bookingId", bookingId);
        result.put("status", status);
        return result;
    }
}
