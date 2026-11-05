package io.microsphere.labs.seata.tcc.order;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

@LocalTCC
public interface OrderTccAction {

    @TwoPhaseBusinessAction(
            name = "orderTccAction",
            commitMethod = "confirm",
            rollbackMethod = "cancel",
            useTCCFence = true
    )
    boolean tryCreate(BusinessActionContext context,
                      @BusinessActionContextParameter(paramName = "orderId") String orderId,
                      @BusinessActionContextParameter(paramName = "skuId") String skuId,
                      @BusinessActionContextParameter(paramName = "userId") String userId,
                      @BusinessActionContextParameter(paramName = "count") int count);

    boolean confirm(BusinessActionContext context);

    boolean cancel(BusinessActionContext context);
}
