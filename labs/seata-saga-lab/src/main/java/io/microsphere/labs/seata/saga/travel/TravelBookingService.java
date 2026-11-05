package io.microsphere.labs.seata.saga.travel;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TravelBookingService {

    private final Map<String, String> bookings = new ConcurrentHashMap<>();

    public Map<String, Object> bookFlight(Map<String, Object> input) {
        return book("flight", input);
    }

    public Map<String, Object> cancelFlight(Map<String, Object> input) {
        return cancel("flight", input);
    }

    public Map<String, Object> bookHotel(Map<String, Object> input) {
        return book("hotel", input);
    }

    public Map<String, Object> cancelHotel(Map<String, Object> input) {
        return cancel("hotel", input);
    }

    public Map<String, Object> bookCar(Map<String, Object> input) {
        if (Boolean.TRUE.equals(input.get("failCar"))) {
            throw new IllegalStateException("car booking failed");
        }
        return book("car", input);
    }

    public Map<String, Object> cancelCar(Map<String, Object> input) {
        return cancel("car", input);
    }

    public Map<String, String> bookings() {
        return new LinkedHashMap<>(bookings);
    }

    private Map<String, Object> book(String type, Map<String, Object> input) {
        Map<String, Object> safeInput = input == null ? Map.of() : input;
        String bookingId = type + ":" + safeInput.get("userId");
        bookings.put(bookingId, "BOOKED");
        return result(bookingId, "BOOKED");
    }

    private Map<String, Object> cancel(String type, Map<String, Object> input) {
        Map<String, Object> safeInput = input == null ? Map.of() : input;
        String bookingId = type + ":" + safeInput.get("userId");
        bookings.put(bookingId, "CANCELLED");
        return result(bookingId, "CANCELLED");
    }

    private Map<String, Object> result(String bookingId, String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bookingId", bookingId);
        result.put("status", status);
        return result;
    }
}
