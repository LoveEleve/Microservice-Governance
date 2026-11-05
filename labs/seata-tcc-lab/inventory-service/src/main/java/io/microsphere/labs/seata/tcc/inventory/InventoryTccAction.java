package io.microsphere.labs.seata.tcc.inventory;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

@LocalTCC
public interface InventoryTccAction {

    @TwoPhaseBusinessAction(
            name = "inventoryTccAction",
            commitMethod = "confirm",
            rollbackMethod = "cancel",
            useTCCFence = true
    )
    boolean tryDeduct(BusinessActionContext context,
                      @BusinessActionContextParameter(paramName = "skuId") String skuId,
                      @BusinessActionContextParameter(paramName = "count") int count);

    boolean confirm(BusinessActionContext context);

    boolean cancel(BusinessActionContext context);
}
