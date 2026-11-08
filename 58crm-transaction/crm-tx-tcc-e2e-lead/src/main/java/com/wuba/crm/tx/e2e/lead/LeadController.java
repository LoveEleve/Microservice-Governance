package com.wuba.crm.tx.e2e.lead;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业务方 HTTP 入口。
 */
@RestController
public class LeadController {

    private final LeadAssignService service;

    /**
     * 创建 controller。
     *
     * @param service 编排服务
     */
    public LeadController(LeadAssignService service) {
        this.service = service;
    }

    /**
     * 分配线索。
     *
     * @param leadId 线索
     * @param userId 用户
     * @return 结果
     */
    @PostMapping("/leads/{leadId}/assign")
    public AssignResult assign(
            @PathVariable("leadId") long leadId,
            @RequestParam("userId") long userId) {
        return service.assign(leadId, userId);
    }
}
