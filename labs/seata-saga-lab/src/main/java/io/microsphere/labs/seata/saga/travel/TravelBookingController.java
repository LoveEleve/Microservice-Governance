package io.microsphere.labs.seata.saga.travel;

import io.seata.saga.engine.StateMachineEngine;
import io.seata.saga.statelang.domain.StateMachineInstance;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/lab/seata/saga/travel")
public class TravelBookingController {

    private final StateMachineEngine stateMachineEngine;
    private final TravelBookingService travelBookingService;

    public TravelBookingController(StateMachineEngine stateMachineEngine,
                                   TravelBookingService travelBookingService) {
        this.stateMachineEngine = stateMachineEngine;
        this.travelBookingService = travelBookingService;
    }

    @PostMapping
    public Map<String, Object> book(@RequestParam String userId,
                                    @RequestParam(defaultValue = "false") boolean failCar) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("userId", userId);
        params.put("failCar", failCar);

        StateMachineInstance instance = stateMachineEngine.start("travelBooking", null, params);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", instance.getStatus().name());
        result.put("bookings", travelBookingService.bookings());
        return result;
    }
}
