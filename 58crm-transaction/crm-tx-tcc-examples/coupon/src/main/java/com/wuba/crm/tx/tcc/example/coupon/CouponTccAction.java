package com.wuba.crm.tx.tcc.example.coupon;

import com.wuba.crm.tx.outbox.api.BusinessEvent;
import com.wuba.crm.tx.outbox.api.OutboxPublisher;
import com.wuba.crm.tx.tcc.api.BusinessActionContext;
import com.wuba.crm.tx.tcc.api.TccAction;
import com.wuba.crm.tx.tcc.api.TccActionHandler;
import com.wuba.crm.tx.tcc.api.TccPhaseResult;
import com.wuba.crm.tx.tcc.api.TccTryResult;
import com.wuba.crm.tx.tcc.example.coupon.persistence.CouponMapper;

/**
 * coupon TCC 动作。
 */
@TccActionHandler("lockCoupon")
public class CouponTccAction implements TccAction {

    private final CouponMapper couponMapper;
    private final OutboxPublisher outboxPublisher;

    /**
     * 创建动作。
     *
     * @param couponMapper    Mapper
     * @param outboxPublisher Outbox
     */
    public CouponTccAction(CouponMapper couponMapper, OutboxPublisher outboxPublisher) {
        this.couponMapper = couponMapper;
        this.outboxPublisher = outboxPublisher;
    }

    @Override
    public TccTryResult tryAction(BusinessActionContext context) {
        return couponMapper.lock(couponId(context)) == 1
                ? TccTryResult.SUCCESS : TccTryResult.FAILURE;
    }

    @Override
    public TccPhaseResult confirmAction(BusinessActionContext context) {
        long couponId = couponId(context);
        if (couponMapper.confirm(couponId) != 1) {
            return TccPhaseResult.PERMANENT_FAILURE;
        }
        outboxPublisher.publish(new BusinessEvent(
                "coupon-confirmed:" + context.xid() + ":" + context.branchId(),
                "coupon",
                String.valueOf(couponId),
                "COUPON_USED",
                "{\"couponId\":" + couponId + "}"));
        return TccPhaseResult.SUCCESS;
    }

    @Override
    public TccPhaseResult cancelAction(BusinessActionContext context) {
        return couponMapper.cancel(couponId(context)) == 1
                ? TccPhaseResult.SUCCESS : TccPhaseResult.PERMANENT_FAILURE;
    }

    private long couponId(BusinessActionContext context) {
        String data = context.applicationData();
        String number = data.replaceAll("[^0-9]", "");
        if (number.isEmpty()) {
            throw new IllegalArgumentException("couponId missing: " + data);
        }
        return Long.parseLong(number);
    }
}
