package com.wuba.crm.tx.tcc.example.lead;

import com.wuba.crm.tx.tcc.api.TccResult;
import com.wuba.crm.tx.tcc.api.TccTransactional;
import org.springframework.stereotype.Service;

/**
 * 业务方 TCC。
 *
 * <p>业务方法只标注 @TccTransactional，分支由 QuotaBranchGateway 的
 * @TccBranch 声明；begin/register/report/commit/rollback 由框架完成。</p>
 */
@Service
public class LeadAssignService {

    private final QuotaBranchGateway quotaGateway;

    /**
     * 创建服务。
     *
     * @param quotaGateway quota 分支网关
     */
    public LeadAssignService(QuotaBranchGateway quotaGateway) {
        this.quotaGateway = quotaGateway;
    }

    /**
     * 分配线索。
     *
     * @param command 命令
     * @return TCC 结果
     */
    @TccTransactional(
            requestId = "#command.requestId",
            bizCode = "lead",
            bizId = "#command.leadId",
            tryTimeoutSeconds = 30)
    public TccResult assign(AssignLeadCommand command) {
        quotaGateway.reserve(command.userId());
        return null;
    }
}
