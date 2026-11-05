package io.microsphere.labs.seata.tcc.order;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class OrderService {

    private final OrderTccAction orderTccAction;
    private final RestTemplate restTemplate;

    public OrderService(OrderTccAction orderTccAction, RestTemplate restTemplate) {
        this.orderTccAction = orderTccAction;
        this.restTemplate = restTemplate;
    }

    @GlobalTransactional(name = "tcc-create-order", rollbackFor = Exception.class)
    public void createOrder(String orderId, String skuId, String userId, int count) {
        boolean created = orderTccAction.tryCreate(
                new BusinessActionContext(), orderId, skuId, userId, count);
        if (!created) {
            throw new IllegalStateException("order create failed: " + orderId);
        }

        Boolean reserved = restTemplate.postForObject(
                "http://127.0.0.1:18084/internal/tcc/inventory/try?skuId={skuId}&count={count}",
                null, Boolean.class, skuId, count);
        if (!Boolean.TRUE.equals(reserved)) {
            throw new IllegalStateException("stock is not enough: " + skuId);
        }
    }
}
