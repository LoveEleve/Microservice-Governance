package com.wuba.crm.tx.tcc.participant.web;

import com.wuba.crm.tx.tcc.api.BusinessActionContext;
import com.wuba.crm.tx.tcc.api.TccTryResult;
import com.wuba.crm.tx.tcc.participant.barrier.TccParticipantService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Participant 通用 Try endpoint。
 *
 * <p>业务方不再为每个动作手写 Try controller。</p>
 */
@RestController
@RequestMapping("/tcc")
public class TccParticipantTryController {

    private final TccParticipantService participantService;

    /**
     * 创建 controller。
     *
     * @param participantService participant 执行器
     */
    public TccParticipantTryController(TccParticipantService participantService) {
        this.participantService = participantService;
    }

    /**
     * 执行 Try。
     *
     * @param request 请求
     * @return 结果
     */
    @PostMapping("/try")
    public TccTryResponse tryAction(@RequestBody TccTryRequest request) {
        TccTryResult result = participantService.tryAction(new BusinessActionContext(
                request.xid(),
                request.branchId(),
                request.actionName(),
                request.applicationData()));
        return new TccTryResponse(result == TccTryResult.SUCCESS, result.name());
    }
}
