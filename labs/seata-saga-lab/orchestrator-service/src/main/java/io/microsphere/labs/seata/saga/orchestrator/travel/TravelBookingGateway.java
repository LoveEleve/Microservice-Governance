package io.microsphere.labs.seata.saga.orchestrator.travel;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class TravelBookingGateway {

    private final RestTemplate restTemplate;

    public TravelBookingGateway(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Map<String, Object> bookFlight(Map<String, Object> input) {
        return post("http://127.0.0.1:18085/internal/flight/book", input);
    }

    public Map<String, Object> cancelFlight(Map<String, Object> input) {
        return post("http://127.0.0.1:18085/internal/flight/cancel", input);
    }

    public Map<String, Object> bookHotel(Map<String, Object> input) {
        return post("http://127.0.0.1:18086/internal/hotel/book", input);
    }

    public Map<String, Object> cancelHotel(Map<String, Object> input) {
        return post("http://127.0.0.1:18086/internal/hotel/cancel", input);
    }

    public Map<String, Object> bookCar(Map<String, Object> input) {
        return post("http://127.0.0.1:18087/internal/car/book", input);
    }

    public Map<String, Object> cancelCar(Map<String, Object> input) {
        return post("http://127.0.0.1:18087/internal/car/cancel", input);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, Map<String, Object> input) {
        return restTemplate.postForObject(url, input, Map.class);
    }
}
