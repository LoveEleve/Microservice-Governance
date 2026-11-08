package com.wuba.crm.tx.tcc.example.lead;

import com.wuba.crm.tx.tcc.api.TccResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 业务方 HTTP 入口。
 */
@RestController
public class LeadController {

    private final LeadAssignService service;

    /**
     * 创建 controller。
     *
     * @param service 业务服务
     */
    public LeadController(LeadAssignService service) {
        this.service = service;
    }

    /**
     * 分配线索。
     *
     * @param leadId   线索
     * @param userId   用户
     * @param couponId 优惠券
     * @return 结果
     */
    @PostMapping("/leads/{leadId}/assign")
    public AssignResult assign(
            @PathVariable("leadId") long leadId,
            @RequestParam("userId") long userId,
            @RequestParam("couponId") long couponId) {
        String requestId = "lead-" + leadId + "-" + userId + "-" + couponId + "-" + UUID.randomUUID();
        TccResult result = service.assign(
                new AssignLeadCommand(requestId, leadId, userId, couponId));
        return new AssignResult(result.xid(), result.status().name());
    }
}
