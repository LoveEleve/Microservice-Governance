package com.wuba.crm.tx.e2e.quota;

import com.wuba.crm.tx.e2e.quota.persistence.QuotaMapper;
import com.wuba.crm.tx.outbox.api.BusinessEvent;
import com.wuba.crm.tx.outbox.api.OutboxPublisher;
import com.wuba.crm.tx.tcc.api.BusinessActionContext;
import com.wuba.crm.tx.tcc.api.TccAction;
import com.wuba.crm.tx.tcc.api.TccPhaseResult;
import com.wuba.crm.tx.tcc.api.TccTryResult;

/**
 * quota TCC 动作。
 *
 * <p>Confirm 内同时写 quota 业务终态和 Outbox，二者与 barrier 同一事务。</p>
 */
public class QuotaTccAction implements TccAction {

    private final QuotaMapper quotaMapper;
    private final OutboxPublisher outboxPublisher;

    /**
     * 创建动作。
     *
     * @param quotaMapper    quota Mapper
     * @param outboxPublisher Outbox 发布器
     */
    public QuotaTccAction(QuotaMapper quotaMapper, OutboxPublisher outboxPublisher) {
        this.quotaMapper = quotaMapper;
        this.outboxPublisher = outboxPublisher;
    }

    /**
     * 预占额度。
     *
     * @param context 上下文
     * @return Try 结果
     */
    @Override
    public TccTryResult tryAction(BusinessActionContext context) {
        return quotaMapper.reserve(userId(context)) == 1
                ? TccTryResult.SUCCESS : TccTryResult.FAILURE;
    }

    /**
     * 正式扣减并写终态事件。
     *
     * @param context 上下文
     * @return Confirm 结果
     */
    @Override
    public TccPhaseResult confirmAction(BusinessActionContext context) {
        long userId = userId(context);
        if (quotaMapper.confirm(userId) != 1) {
            return TccPhaseResult.PERMANENT_FAILURE;
        }
        outboxPublisher.publish(new BusinessEvent(
                "quota-confirmed:" + context.xid() + ":" + context.branchId(),
                "quota",
                String.valueOf(userId),
                "QUOTA_CONFIRMED",
                "{\"userId\":" + userId + "}"));
        return TccPhaseResult.SUCCESS;
    }

    /**
     * 释放预占。
     *
     * @param context 上下文
     * @return Cancel 结果
     */
    @Override
    public TccPhaseResult cancelAction(BusinessActionContext context) {
        return quotaMapper.cancel(userId(context)) == 1
                ? TccPhaseResult.SUCCESS : TccPhaseResult.PERMANENT_FAILURE;
    }

    private long userId(BusinessActionContext context) {
        String data = context.applicationData();
        String number = data.replaceAll("[^0-9]", "");
        if (number.isEmpty()) {
            throw new IllegalArgumentException("userId missing: " + data);
        }
        return Long.parseLong(number);
    }
}
