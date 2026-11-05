package io.microsphere.labs.seata.tcc.inventory;

import io.seata.rm.tcc.api.BusinessActionContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/tcc/inventory")
public class InventoryController {

    private final InventoryTccAction inventoryTccAction;

    public InventoryController(InventoryTccAction inventoryTccAction) {
        this.inventoryTccAction = inventoryTccAction;
    }

    @PostMapping("/try")
    public boolean tryDeduct(@RequestParam String skuId, @RequestParam int count) {
        return inventoryTccAction.tryDeduct(new BusinessActionContext(), skuId, count);
    }
}
