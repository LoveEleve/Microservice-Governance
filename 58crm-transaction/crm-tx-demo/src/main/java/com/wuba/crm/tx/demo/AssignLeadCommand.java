package com.wuba.crm.tx.demo;

/**
 * 业务方发起“线索分配”的请求。
 *
 * @param leadId    线索 ID
 * @param salesId   销售 ID
 * @param requestId 业务请求幂等键
 */
public record AssignLeadCommand(long leadId, long salesId, String requestId) {

    public AssignLeadCommand {
        if (leadId <= 0 || salesId <= 0) {
            throw new IllegalArgumentException("leadId/salesId must be positive");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
    }
}
