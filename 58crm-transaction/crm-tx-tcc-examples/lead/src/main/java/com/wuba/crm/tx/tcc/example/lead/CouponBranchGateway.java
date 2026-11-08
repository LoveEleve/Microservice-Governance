package com.wuba.crm.tx.tcc.example.lead;

import com.wuba.crm.tx.tcc.api.TccBranch;
import com.wuba.crm.tx.tcc.api.TccContext;
import com.wuba.crm.tx.tcc.api.TccTryResult;
import org.springframework.stereotype.Service;

/**
 * 第二个分支网关：注解负责注册/上报，内部通过 OpenFeign 调 coupon Try。
 */
@Service
public class CouponBranchGateway {

    private final CouponFeignClient couponClient;

    /**
     * 创建网关。
     *
     * @param couponClient coupon Feign
     */
    public CouponBranchGateway(CouponFeignClient couponClient) {
        this.couponClient = couponClient;
    }

    /**
     * 调用 coupon Try。
     *
     * @param couponId 优惠券
     * @return Try 结果
     */
    @TccBranch(
            actionName = "lockCoupon",
            resourceKey = "coupon:#{#couponId}",
            callbackEndpoint = "${coupon.callback-endpoint}",
            applicationData = "#{#couponId}")
    public TccTryResult lock(long couponId) {
        TccContext context = TccContext.current();
        TryCouponResponse response = couponClient.tryCoupon(new TryCouponRequest(
                context.xid(),
                context.branchId(),
                "lockCoupon",
                "{\"couponId\":" + couponId + "}"));
        return response.success() ? TccTryResult.SUCCESS : TccTryResult.FAILURE;
    }
}
