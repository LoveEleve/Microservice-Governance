package com.wuba.crm.tx.e2e.quota;

import com.wuba.crm.tx.tcc.api.BusinessActionContext;
import com.wuba.crm.tx.tcc.api.TccTryResult;
import com.wuba.crm.tx.tcc.participant.barrier.TccParticipantService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * quota Try HTTP 入口，由业务方通过 OpenFeign 调用。
 */
@RestController
public class QuotaController {

    private final TccParticipantService participantService;

    /**
     * 创建 controller。
     *
     * @param participantService participant 执行器
     */
    public QuotaController(TccParticipantService participantService) {
        this.participantService = participantService;
    }

    /**
     * 执行 quota Try。
     *
     * @param request 请求
     * @return 结果
     */
    @PostMapping("/quota/try")
    public TryQuotaResponse tryQuota(@RequestBody TryQuotaRequest request) {
        TccTryResult result = participantService.tryAction(new BusinessActionContext(
                request.xid(),
                request.branchId(),
                "reserveQuota",
                "{\"userId\":" + request.userId() + "}"));
        return new TryQuotaResponse(
                result == TccTryResult.SUCCESS,
                result.name());
    }
}
